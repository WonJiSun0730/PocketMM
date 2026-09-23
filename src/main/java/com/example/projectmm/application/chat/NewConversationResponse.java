package com.example.projectmm.application.chat;

import java.time.LocalDateTime;
import java.util.List;

public record NewConversationResponse(Long conversationId, LocalDateTime simulatedAt,
                                      List<ChatHistoryResponse> messages) {}
