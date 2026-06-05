(function () {
    const chatForm = document.getElementById("chatForm");
    const chatInput = document.getElementById("chatInput");
    const chatMessages = document.getElementById("chatMessages");
    const clearChatButton = document.getElementById("clearChatButton");
    const chatError = document.getElementById("chatError");

    let chatHistory = [];

    function ensureAdminSession() {
        if (!window.ddukSession || typeof window.ddukSession.requireRole !== "function") {
            return true;
        }
        return Boolean(window.ddukSession.requireRole(["ADMIN"]));
    }

    function setErrorMessage(text) {
        if (!chatError) return;
        if (text) {
            chatError.textContent = text;
            chatError.classList.remove("hidden");
        } else {
            chatError.textContent = "";
            chatError.classList.add("hidden");
        }
    }

    function appendMessage(role, text) {
        if (!chatMessages) return;

        const messageDiv = document.createElement("div");
        messageDiv.className = `flex ${role === "user" ? "justify-end" : "justify-start"} mb-4`;

        const innerDiv = document.createElement("div");
        // HSL 기반 또는 세련된 컬러 사용
        if (role === "user") {
            innerDiv.className = "max-w-[80%] rounded-2xl px-4 py-3 bg-indigo-600 text-white text-sm shadow-sm font-medium whitespace-pre-wrap break-all";
        } else {
            innerDiv.className = "max-w-[80%] rounded-2xl px-4 py-3 bg-gray-100 text-gray-800 text-sm shadow-sm border border-gray-200/50 whitespace-pre-wrap break-all";
        }
        
        innerDiv.textContent = text;
        messageDiv.appendChild(innerDiv);
        chatMessages.appendChild(messageDiv);

        // 스크롤 아래로 이동
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    function appendLoading() {
        if (!chatMessages) return null;

        const loadingDiv = document.createElement("div");
        loadingDiv.id = "chat-loading-bubble";
        loadingDiv.className = "flex justify-start mb-4";

        const innerDiv = document.createElement("div");
        innerDiv.className = "max-w-[80%] rounded-2xl px-4 py-3 bg-gray-100 text-gray-500 text-sm shadow-sm border border-gray-200/50 flex items-center gap-2";
        innerDiv.innerHTML = `
            <svg class="animate-spin h-4 w-4 text-indigo-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <span>AI 응답을 생성하는 중...</span>
        `;

        loadingDiv.appendChild(innerDiv);
        chatMessages.appendChild(loadingDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;

        return loadingDiv;
    }

    function removeLoading() {
        const loadingDiv = document.getElementById("chat-loading-bubble");
        if (loadingDiv) {
            loadingDiv.remove();
        }
    }

    async function sendChatMessage(message) {
        setErrorMessage(null);
        const loadingBubble = appendLoading();

        try {
            const response = await fetch(`${window.ddukSession.getApiBaseUrl()}/api/v1/ai/chat`, {
                method: "POST",
                headers: window.ddukSession.getAuthHeaders({
                    "Content-Type": "application/json"
                }),
                body: JSON.stringify({
                    message: message,
                    history: chatHistory
                })
            });

            const text = await response.text();
            let payload;
            try {
                payload = text ? JSON.parse(text) : null;
            } catch (e) {
                throw new Error("서버 응답 형식이 유효하지 않습니다.");
            }

            removeLoading();

            if (!response.ok || !payload) {
                const errMsg = payload && payload.message ? payload.message : `요청 처리에 실패했습니다. (HTTP ${response.status})`;
                throw new Error(errMsg);
            }

            if (payload.status === "success" && payload.data && payload.data.response) {
                const reply = payload.data.response;
                appendMessage("model", reply);
                
                // 대화 히스토리 업데이트
                chatHistory.push({ role: "user", content: message });
                chatHistory.push({ role: "model", content: reply });
            } else {
                throw new Error(payload.message || "응답 데이터를 불러오지 못했습니다.");
            }
        } catch (error) {
            removeLoading();
            setErrorMessage(error.message);
        }
    }

    if (chatForm) {
        chatForm.addEventListener("submit", async function (event) {
            event.preventDefault();
            const message = chatInput.value.trim();
            if (!message) return;

            // 입력 필드 비우기 및 포커스 유지
            chatInput.value = "";
            appendMessage("user", message);

            await sendChatMessage(message);
        });
    }

    if (clearChatButton) {
        clearChatButton.addEventListener("click", function () {
            if (chatMessages) {
                chatMessages.innerHTML = `
                    <div class="flex justify-start mb-4">
                        <div class="max-w-[80%] rounded-2xl px-4 py-3 bg-gray-50 text-gray-500 text-sm border border-gray-100 italic">
                            대화가 초기화되었습니다. 새로운 질문을 입력해 보세요.
                        </div>
                    </div>
                `;
            }
            chatHistory = [];
            setErrorMessage(null);
        });
    }

    if (ensureAdminSession()) {
        // 초기 웰컴 메시지
        if (chatMessages && chatMessages.children.length === 0) {
            appendMessage("model", "안녕하세요! DDUK ERP AI 테스트 챗봇입니다. AI 프록시 기능과 응답 품질을 점검해 보실 수 있습니다. 궁금하신 내용을 질문해 주세요.");
        }
    }
})();
