#include "comm.h"

#include <string>
#include <stdbool.h>
#include <unistd.h>

#include <public/fpdf_ext.h>
#include <public/fpdfview.h>
#include <hk_file.h>
#include <public/cpp/fpdf_scopers.h>

#include <android/bitmap.h>

template<class string_type>
inline typename string_type::value_type *WriteInto(string_type *str, size_t length_with_null) {
    str->reserve(length_with_null);
    str->resize(length_with_null - 1);
    return &((*str)[0]);
}

extern "C" {

static int sLibraryReferenceCount = 0;

void UnsupportedInfoHandler(UNSUPPORT_INFO *, int type) {
    std::string feature = "Unknown";
    switch (type) {
        case FPDF_UNSP_DOC_XFAFORM:
            feature = "XFA";
            break;
        case FPDF_UNSP_DOC_PORTABLECOLLECTION:
            feature = "Portfolios_Packages";
            break;
        case FPDF_UNSP_DOC_ATTACHMENT:
        case FPDF_UNSP_ANNOT_ATTACHMENT:
            feature = "Attachment";
            break;
        case FPDF_UNSP_DOC_SECURITY:
            feature = "Rights_Management";
            break;
        case FPDF_UNSP_DOC_SHAREDREVIEW:
            feature = "Shared_Review";
            break;
        case FPDF_UNSP_DOC_SHAREDFORM_ACROBAT:
        case FPDF_UNSP_DOC_SHAREDFORM_FILESYSTEM:
        case FPDF_UNSP_DOC_SHAREDFORM_EMAIL:
            feature = "Shared_Form";
            break;
        case FPDF_UNSP_ANNOT_3DANNOT:
            feature = "3D";
            break;
        case FPDF_UNSP_ANNOT_MOVIE:
            feature = "Movie";
            break;
        case FPDF_UNSP_ANNOT_SOUND:
            feature = "Sound";
            break;
        case FPDF_UNSP_ANNOT_SCREEN_MEDIA:
        case FPDF_UNSP_ANNOT_SCREEN_RICHMEDIA:
            feature = "Screen";
            break;
        case FPDF_UNSP_ANNOT_SIG:
            feature = "Digital_Signature";
            break;
    }
    LOGE("Unsupported feature: %s.\n", feature.c_str());
}

static void initLibraryIfNeed() {
    if (sLibraryReferenceCount == 0) {
        FPDF_LIBRARY_CONFIG config;
        config.version = 3;
        config.m_pUserFontPaths = nullptr;
        config.m_pIsolate = nullptr;
        config.m_v8EmbedderSlot = 0;
        config.m_pPlatform = nullptr;

        FPDF_InitLibraryWithConfig(&config);

        UNSUPPORT_INFO unsupported_info = {};
        unsupported_info.version = 1;
        unsupported_info.FSDK_UnSupport_Handler = UnsupportedInfoHandler;
        LOGI("PDFSDK Library Initialized!");
    }
}

static void destroyLibraryIfNeed() {
//    Mutex::Autolock lock(sLibraryLock);
    sLibraryReferenceCount--;
    if (sLibraryReferenceCount == 0) {
        FPDF_DestroyLibrary();
        LOGI("PDFSDK Instance Destroyed!");
    }
}

int jniThrowException(JNIEnv *env, const char *className, const char *message) {
    jclass exClass = env->FindClass(className);
    if (exClass == NULL) {
        LOGE("Unable to find exception class %s", className);
        return -1;
    }

    if (env->ThrowNew(exClass, message) != JNI_OK) {
        LOGE("Failed throwing '%s' '%s'", className, message);
        return -1;
    }

    return 0;
}

struct rgb {
    uint8_t red;
    uint8_t green;
    uint8_t blue;
};

jobject NewLong(JNIEnv *env, jlong value) {
    jclass cls = env->FindClass("java/lang/Long");
    jmethodID methodID = env->GetMethodID(cls, "<init>", "(J)V");
    return env->NewObject(cls, methodID, value);
}

jobject NewInteger(JNIEnv *env, jint value) {
    jclass cls = env->FindClass("java/lang/Integer");
    jmethodID methodID = env->GetMethodID(cls, "<init>", "(I)V");
    return env->NewObject(cls, methodID, value);
}

uint16_t rgb_to_565(unsigned char R8, unsigned char G8, unsigned char B8) {
    unsigned char R5 = (R8 * 249 + 1014) >> 11;
    unsigned char G6 = (G8 * 253 + 505) >> 10;
    unsigned char B5 = (B8 * 249 + 1014) >> 11;
    return (R5 << 11) | (G6 << 5) | (B5);
}

void rgbBitmapTo565(void *source, int sourceStride, void *dest, AndroidBitmapInfo *info) {
    rgb *srcLine;
    uint16_t *dstLine;
    int y, x;
    for (y = 0; y < info->height; y++) {
        srcLine = (rgb *) source;
        dstLine = (uint16_t *) dest;
        for (x = 0; x < info->width; x++) {
            rgb *r = &srcLine[x];
            dstLine[x] = rgb_to_565(r->red, r->green, r->blue);
        }
        source = (char *) source + sourceStride;
        dest = (char *) dest + info->stride;
    }
}

class DocumentFile {
public:
    ScopedFPDFDocument pdfDocument = nullptr;

    DocumentFile() { initLibraryIfNeed(); }

    virtual ~DocumentFile();
};

DocumentFile::~DocumentFile() {
    destroyLibraryIfNeed();
}

static int getBlock(void *param, unsigned long position, unsigned char *outBuffer,
                    unsigned long size) {
    const int fd = (int)reinterpret_cast<intptr_t>(param);
    const int readCount = pread(fd, outBuffer, size, position);
    if (readCount < 0) {
        LOGE("Cannot read from file descriptor.");
        return 0;
    }
    return 1;
}

static jlong loadPageInternal(JNIEnv *env, DocumentFile *doc, int pageIndex) {
    try {
        if (doc == nullptr) throw "Get page document null";

        if (doc->pdfDocument.get() != nullptr) {
            FPDF_PAGE page = FPDF_LoadPage(doc->pdfDocument.get(), pageIndex);
            if (page == nullptr) {
                throw "Loaded page is NULL";
            }

            return reinterpret_cast<jlong>(page);
        } else {
            throw "Get page pdf document null";
        }
    } catch (const char *msg) {
        LOGE("%s", msg);

        jniThrowException(env, "java/lang/IllegalStateException",
                          "cannot load page");

        return -1;
    }
}

static void closePageInternal(jlong pagePtr) {
    FPDF_ClosePage(reinterpret_cast<FPDF_PAGE>(pagePtr));
}

unsigned long ConvertLastError(char *buf, size_t buf_len) {
    unsigned long err = FPDF_GetLastError();
    switch (err) {
        case FPDF_ERR_SUCCESS:
            snprintf(buf, buf_len, "Success");
        case FPDF_ERR_UNKNOWN:
            snprintf(buf, buf_len, "Unknown error");
        case FPDF_ERR_FILE:
            snprintf(buf, buf_len, "File not found or could not be opened");
        case FPDF_ERR_FORMAT:
            snprintf(buf, buf_len, "File not in PDF format or corrupted");
        case FPDF_ERR_PASSWORD:
            snprintf(buf, buf_len, "Password required or incorrect password");
        case FPDF_ERR_SECURITY:
            snprintf(buf, buf_len, "Unsupported security scheme");
        case FPDF_ERR_PAGE:
            snprintf(buf, buf_len, "Page not found or content error");
        default:
            snprintf(buf, buf_len,"Unknown error %ld", err);
    }
    return err;
}

JNI_FUNC(jlong, PdfiumSDK, nativeOpenDocument)(JNI_ARGS, jint fd, jstring password) {
    int ret = -1;
    if (fd < 0) {
        jniThrowException(env, "java/io/IOException",
                          "file descriptor must be greater than or equal to 0");
        return ret;
    }
    ssize_t fileLen = fs_get_size_for_fd(fd);
    if (fileLen <= 0) {
        jniThrowException(env, "java/io/IOException",
                          "File is empty");
        return ret;
    }

    DocumentFile *docFile = new DocumentFile();

    FPDF_FILEACCESS file_access = {};
    file_access.m_FileLen = static_cast<unsigned long>(fileLen);
    file_access.m_GetBlock = &getBlock;
    file_access.m_Param = reinterpret_cast<void *>(intptr_t(fd));

    const char *cpassword = NULL;
    if (password != NULL) {
        cpassword = env->GetStringUTFChars(password, NULL);
    }

    FPDF_DOCUMENT document = FPDF_LoadCustomDocument(&file_access, cpassword);

    if (cpassword != NULL) {
        env->ReleaseStringUTFChars(password, cpassword);
    }

    if (!document) {
        delete docFile;

        char errMsg[256];
        ConvertLastError(errMsg, sizeof(errMsg));
        jniThrowException(env, "java/io/IOException", errMsg);
        ret = -1;
        return ret;
    }

    docFile->pdfDocument.reset(document);

    ret = reinterpret_cast<jlong>(docFile);
    return ret;
}

JNI_FUNC(jlong, PdfiumSDK, nativeOpenMemDocument)(JNI_ARGS, jbyteArray data, jstring password) {
    return -1;
}

JNI_FUNC(jint, PdfiumSDK, nativeGetPageCount)(JNI_ARGS, jlong documentPtr) {
    DocumentFile *doc = reinterpret_cast<DocumentFile *>(documentPtr);
    return (jint)FPDF_GetPageCount(doc->pdfDocument.get());
}

JNI_FUNC(void, PdfiumSDK, nativeCloseDocument)(JNI_ARGS, jlong documentPtr) {
    DocumentFile *doc = reinterpret_cast<DocumentFile *>(documentPtr);
    delete doc;
}

JNI_FUNC(jstring, PdfiumSDK, nativeGetDocumentMetaText)(JNI_ARGS, jlong documentPtr, jstring tag) {
    const char *ctag = env->GetStringUTFChars(tag, NULL);
    if (ctag == NULL) {
        return env->NewStringUTF("");
    }
    DocumentFile *doc = reinterpret_cast<DocumentFile *>(documentPtr);
    const unsigned long bufferLen = FPDF_GetMetaText(doc->pdfDocument.get(), ctag, NULL, 0);
    if (bufferLen <= 2) {
        return env->NewStringUTF("");
    }

    std::wstring text;
    FPDF_GetMetaText(doc->pdfDocument.get(), ctag, WriteInto(&text, bufferLen + 1), bufferLen);
    env->ReleaseStringUTFChars(tag, ctag);
    return env->NewString((jchar *)text.c_str(), bufferLen / 2 - 1);
}

JNI_FUNC(jlong, PdfiumSDK, nativeLoadPage)(JNI_ARGS, jlong documentPtr, jint pageIndex) {
    DocumentFile *doc = reinterpret_cast<DocumentFile *>(documentPtr);
    return loadPageInternal(env, doc, pageIndex);
}

JNI_FUNC(void, PdfiumSDK, nativeClosePage)(JNI_ARGS, jlong pagePtr) { closePageInternal(pagePtr); }
JNI_FUNC(void, PdfiumSDK, nativeClosePages)(JNI_ARGS, jlongArray pagesPtr) {
    int length = (int) (env->GetArrayLength(pagesPtr));
    jlong *pages = env->GetLongArrayElements(pagesPtr, NULL);

    int i;
    for (i = 0; i < length; i++) { closePageInternal(pages[i]); }
}

JNI_FUNC(jobject, PdfiumSDK, nativeGetPageSizeByIndex)(JNI_ARGS, jlong docPtr, jint pageIndex, jint dpi) {
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(docPtr);
    if (doc == NULL) {
        LOGE("Document is null");
        jniThrowException(env, "java/lang/IllegalStateException", "Document is null");
        return NULL;
    }

    double width, height;
    int result = FPDF_GetPageSizeByIndex(doc->pdfDocument.get(), pageIndex, &width, &height);
    if (result == 0) {
        width = 0;
        height = 0;
    }
    jint widthInt = (jint) (width * dpi / 72);
    jint heightInt = (jint) (height * dpi / 72);
    jclass clazz = env->FindClass("com/hungknow/pdfsdk/models/Size");
    jmethodID constructorId = env->GetMethodID(clazz, "<init>", "(II)V");
    return env->NewObject(clazz, constructorId, widthInt, heightInt);
}

JNI_FUNC(void, PdfiumSDK, nativeRenderPageBitmap)(JNI_ARGS, jlong pagePtr, jobject bitmap,
                                                  jint dpi, jint startX, jint startY,
                                                  jint drawSizeHor, jint drawSizeVer,
                                                  jboolean renderAnnot) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    if (page == NULL || bitmap == NULL) {
        LOGE("Render page pointers invalid");
        return;
    }

    AndroidBitmapInfo info;
    int ret;
    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("Fetching bitmap info failed: %s", strerror(ret * -1));
        return;
    }

    int canvasHorSize = info.width;
    int canvasVerSize = info.height;

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 &&
        info.format != ANDROID_BITMAP_FORMAT_RGB_565) {
        LOGE("Bitmap format must be RGBA_8888 or RGB_565");
        return;
    }

    void *addr;
    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &addr)) != 0) {
        LOGE("Locking bitmap failed: %s", strerror(ret * -1));
        return;
    }

    void *tmp;
    int format;
    int sourceStride;
    if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        tmp = malloc(canvasVerSize * canvasHorSize * sizeof(rgb));
        sourceStride = canvasHorSize * sizeof(rgb);
        format = FPDFBitmap_BGR;
    } else {
        tmp = addr;
        sourceStride = info.stride;
        format = FPDFBitmap_BGRA;
    }

    FPDF_BITMAP pdfBitmap = FPDFBitmap_CreateEx(canvasHorSize, canvasVerSize,
                                                format, tmp, sourceStride);

    if (drawSizeHor < canvasHorSize || drawSizeVer < canvasVerSize) {
        FPDFBitmap_FillRect(pdfBitmap, 0, 0, canvasHorSize, canvasVerSize,
                            0x848484FF); //Gray
    }

    int baseHorSize = (canvasHorSize < drawSizeHor) ? canvasHorSize : (int) drawSizeHor;
    int baseVerSize = (canvasVerSize < drawSizeVer) ? canvasVerSize : (int) drawSizeVer;
    int baseX = (startX < 0) ? 0 : (int) startX;
    int baseY = (startY < 0) ? 0 : (int) startY;
    int flags = FPDF_REVERSE_BYTE_ORDER;

    if (renderAnnot) {
        flags |= FPDF_ANNOT;
    }

    if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        FPDFBitmap_FillRect(pdfBitmap, baseX, baseY, baseHorSize, baseVerSize,
                            0xFFFFFFFF); //White
    }

    FPDF_RenderPageBitmap(pdfBitmap, page,
                          startX, startY,
                          (int) drawSizeHor, (int) drawSizeVer,
                          0, flags);

    if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        rgbBitmapTo565(tmp, sourceStride, addr, &info);
        free(tmp);
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}
///////////////////////////////////////
// PDF TextPage api
///////////
static void closeTextPageInternal(jlong textPagePtr) {
    FPDFText_ClosePage(reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr));
}

JNI_FUNC(void, PdfiumSDK, nativeCloseTextPage)(JNI_ARGS, jlong textPagePtr) {
    closeTextPageInternal(textPagePtr);
}

} // extern "C"