(function () {
    const shared = window.OcrDocumentsShared;
    if (!shared) {
        throw new Error("OcrDocumentsShared is required before ocr-upload-page.js");
    }

    const { requestApi, setMessage } = shared;

    function initUploadPage() {
        const form = document.getElementById("ocrUploadForm");
        if (!form) {
            return;
        }

        const message = document.getElementById("uploadMessage");
        const submitButton = document.getElementById("uploadSubmitBtn");
        const resultBox = document.getElementById("uploadResult");

        if (!window.ddukSession.requireRole(["ADMIN", "HR", "INVENTORY"])) {
            return;
        }

        form.addEventListener("submit", async function (event) {
            event.preventDefault();

            const fileInput = document.getElementById("ocrFile");
            const documentType = document.getElementById("documentType").value;

            if (!fileInput.files || fileInput.files.length === 0) {
                setMessage(message, "업로드할 파일을 선택해 주세요.", "error");
                return;
            }

            const formData = new FormData();
            formData.append("file", fileInput.files[0]);
            formData.append("documentType", documentType);

            submitButton.disabled = true;
            submitButton.textContent = "업로드 중...";
            setMessage(message, "", "");
            resultBox.classList.add("hidden");

            try {
                const data = await requestApi("/api/v1/ai/ocr/documents", "OCR 문서 업로드에 실패했습니다.", {
                    method: "POST",
                    body: formData
                });
                document.getElementById("resultDocumentId").textContent = data.id;
                document.getElementById("resultFilename").textContent = data.originalFilename;
                document.getElementById("resultStatus").textContent = data.processingStatus;
                resultBox.classList.remove("hidden");
                setMessage(message, data.message || "OCR 문서를 등록했습니다.", "success");
                form.reset();
            } catch (error) {
                setMessage(message, error.message, "error");
            } finally {
                submitButton.disabled = false;
                submitButton.textContent = "업로드";
            }
        });
    }

    initUploadPage();
})();
