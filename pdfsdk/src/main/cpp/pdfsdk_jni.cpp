#include "comm.h"

#include <string>

#include <public/fpdf_ext.h>
#include <public/fpdfview.h>

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

class DocumentFile {
public:
    FPDF_DOCUMENT pdfDocument = nullptr;

    DocumentFile() { initLibraryIfNeed(); }

    virtual ~DocumentFile();
};

DocumentFile::~DocumentFile() {
    if (pdfDocument != NULL) {
        FPDF_CloseDocument(pdfDocument);
    }

    destroyLibraryIfNeed();
}

JNI_FUNC(jlong, PdfiumSDK, nativeOpenDocument)(JNI_ARGS, jint fd, jstring password) {
    return -1;
}

JNI_FUNC(jlong, PdfiumSDK, nativeOpenMemDocument)(JNI_ARGS, jbyteArray data, jstring password) {
    return -1;
}

} // extern "C"