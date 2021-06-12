#include "comm.h"

#include <string>
#include <stdbool.h>
#include <unistd.h>

#include <public/fpdf_ext.h>
#include <public/fpdfview.h>
#include <hk_file.h>
#include <public/cpp/fpdf_scopers.h>

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

} // extern "C"