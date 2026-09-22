const characterId = 1;

const chatMessages =
    document.getElementById("chat-messages");

const chatForm =
    document.getElementById("chat-form");

const messageInput =
    document.getElementById("message-input");


async function loadMessages() {
    const response =
        await fetch(
            `/api/chat/${characterId}/messages`
        );

    if (!response.ok) {
        throw new Error(
            `Failed to load messages: ${response.status}`
        );
    }

    const messages =
        await response.json();

    chatMessages.innerHTML = "";

    for (const message of messages) {
        const messageElement =
            document.createElement("div");

        messageElement.textContent =
            `${message.role}: ${message.message}`;

        chatMessages.appendChild(
            messageElement
        );
    }

    scrollToBottom();
}


async function sendMessage(message) {
    const response =
        await fetch(
            `/api/chat/${characterId}`,
            {
                method: "POST",

                headers: {
                    "Content-Type":
                        "application/json"
                },

                body: JSON.stringify({
                    message: message,
                    contentMode: "NORMAL"
                })
            }
        );

    if (!response.ok) {
        throw new Error(
            `Failed to send message: ${response.status}`
        );
    }

    return response.json();
}


function scrollToBottom() {
    chatMessages.scrollTop =
        chatMessages.scrollHeight;
}


chatForm.addEventListener(
    "submit",
    async (event) => {
        event.preventDefault();

        const message =
            messageInput.value.trim();

        if (!message) {
            return;
        }

        messageInput.value = "";
        messageInput.disabled = true;

        try {
            await sendMessage(message);

            await loadMessages();

        } catch (error) {
            console.error(error);

            alert(
                "메시지 전송에 실패했습니다."
            );

        } finally {
            messageInput.disabled = false;
            messageInput.focus();
        }
    }
);


loadMessages()
    .catch(error => {
        console.error(error);

        chatMessages.textContent =
            "대화를 불러오지 못했습니다.";
    });