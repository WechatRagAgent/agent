package com.wechat.rag.core.agent.injector;

import com.wechat.rag.core.constants.CommonConstant;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 元数据内容注入器
 * 基于LangChain4j官方示例实现，正确返回List<ChatMessage>
 * 将检索到的内容格式化为SystemMessage注入到对话上下文中
 */
@Component
@Slf4j
public class MetadataContentInjector implements ContentInjector {

    // 时间格式化器
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Override
    public ChatMessage inject(List<Content> contents, ChatMessage chatMessage) {
        // 如果没有检索到内容，返回原始消息
        if (contents == null || contents.isEmpty()) {
            log.info("没有检索到相关内容，返回原始消息");
        } else {
            log.info("注入 {} 条内容到用户消息中", contents.size());
        }

        // 格式化检索到的内容
        String formattedContext = formatRetrievedContents(contents);

        // 获取原始消息文本
        String originalText = extractMessageText(chatMessage);
        if (StringUtils.isNotEmpty(originalText)) {
            originalText = originalText.replaceAll(CommonConstant.CONTEXT_PATTERN.pattern(), "");
        }
        // 构建增强的消息文本
        String enhancedText = buildEnhancedMessage(originalText, formattedContext);

        // 返回增强后的UserMessage
        return UserMessage.from(enhancedText);
    }

    /**
     * 提取消息文本
     */
    private String extractMessageText(ChatMessage chatMessage) {
        if (chatMessage instanceof UserMessage) {
            return ((UserMessage) chatMessage).singleText();
        }
        // 对于其他类型的消息，尝试转换为字符串
        return chatMessage.toString();
    }

    /**
     * 构建增强的消息文本
     */
    private String buildEnhancedMessage(String originalText, String formattedContext) {
        return String.format("""
                ### 角色
                您是一个敏锐的社交观察员和故事讲述者。您的目标是基于下方提供的聊天记录，用生动、自然且易于理解的方式，为用户的提问提供最直接、最有帮助、最易于理解的回答。
                
                ### 核心指令
                1.  **基于事实**:
                    * 所有的解读和推断都必须**植根于聊天记录**，不能凭空捏造。当您描述一个场景或互动时，请确保聊天记录中有支撑您描述的证据。
                    * 您的回答应该像一个故事或概述，用连贯的段落形式呈现。请聚焦于群聊的**整体氛围、主要议题和成员间的有趣互动**，而不是死板地罗列谁说了什么。
                    * 您可以适度进行**解读和推断**，将碎片化的信息（如“嗯”、“哈哈”）融入到对整体对话情绪的描述中
                2.  **实体身份解析（关键步骤）**:
                    * 在分析前，您必须先进行身份识别。对于任何“发送者”字段为空的消息，您必须查看其“发送者ID”。
                    * 您需要检索全部聊天记录，找到至少一条“发送者ID”相同且“发送者”字段**不为空**的记录。
                    * 如果找到，请将这条匿名消息的“发送者”认定为已识别出的姓名。例如，如果ID为 "A" 的一条消息缺少名字，但另一条ID为 "A" 的消息发送者是“小宇宙”，那么所有ID为 "A" 的消息都应归属于“小宇宙”。
                    * 只有在通过此方法后，仍然无法确定发送者身份的消息，才可被归类为“无法识别的成员”。
                3.  **智能选择格式**：请根据“用户问题”的性质，选择最合适的格式来回答。
                    - 如果问题是要求**罗列**事实或观点（例如，“列出所有风险”、“有哪些建议”），请使用清晰的**无序列表**（以'-'开头）。
                    - 如果问题是要求**总结**一段对话或一个事件，请使用连贯、分段的**自然段落**。
                    - 如果问题是要求进行**分析**（例如，分析人物性格、分析事件原因），请使用结构化的**分析性段落**，可以适当使用标题或加粗来突出重点。
                    - 如果是简单的直接提问，请给出直接的答案。
                4.  **专业且自然的语气**:
                    * # 关键改动点：强调自然感
                    * 您的语气应该像一个朋友在转述一件有趣的事情，既乐于助人又生动自然。
                5.  当在回答中提到具体人物时，请使用聊天记录中出现的全名以确保清晰。
                6.  如果“相关聊天记录”中完全不包含回答问题所需的信息，请只回答一句话：“根据提供的聊天记录，未能找到相关信息。”
                7.  你的语气应该乐于助人、客观中立且专业。
                
                ### 相关聊天记录
                ---
                %s
                ---
                
                ### 用户问题
                %s
                """, formattedContext, originalText);
    }

    /**
     * 格式化检索到的内容列表
     */
    private String formatRetrievedContents(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return "没有检索到相关内容";
        }
        return IntStream.range(0, contents.size())
                .mapToObj(i -> {
                    Content content = contents.get(i);
                    return formatSingleContent(content, i + 1);
                })
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 格式化单条内容，包含元数据信息
     */
    private String formatSingleContent(Content content, int index) {
        TextSegment segment = content.textSegment();
        if (segment == null) {
            return "内容: 空";
        }

        StringBuilder formatted = new StringBuilder();

        // 添加元数据信息
        if (segment.metadata() != null && !segment.metadata().toMap().isEmpty()) {
            String metadataInfo = buildMetadataInfo(segment);
            if (!metadataInfo.isEmpty()) {
                formatted.append(metadataInfo).append("\n");
            }
        }

        // 添加文本内容
        formatted.append("内容: ").append(segment.text());
        // 添加引用内容
        String refer = segment.metadata().getString("refer");
        if (StringUtils.isNotEmpty(refer)) {
            formatted.append("\n引用: ").append(refer);
        }
        return formatted.toString();
    }

    /**
     * 构建元数据信息字符串
     */
    private String buildMetadataInfo(TextSegment segment) {
        StringBuilder metadataBuilder = new StringBuilder();

        // 发送者信息
        String senderName = segment.metadata().getString("senderName");
        if (senderName != null) {
            metadataBuilder.append("发送者: ").append(senderName);
        }
        // sender
        String senderId = segment.metadata().getString("sender");
        if (senderId != null) {
            if (metadataBuilder.length() > 0) metadataBuilder.append(" | ");
            metadataBuilder.append("发送者ID: ").append(senderId);
        }

        // 对话群组信息
        String talkerName = segment.metadata().getString("talkerName");
        if (talkerName != null) {
            if (metadataBuilder.length() > 0) metadataBuilder.append(" | ");
            metadataBuilder.append("群名: ").append(talkerName);
        }

        // 时间信息
        String timeStr = segment.metadata().getString("time");
        if (timeStr != null) {
            if (metadataBuilder.length() > 0) metadataBuilder.append(" | ");
            metadataBuilder.append("时间: ").append(formatTime(timeStr));
        }

        // 消息类型
        Object typeObj = segment.metadata().toMap().get("type");
        if (typeObj != null) {
            if (metadataBuilder.length() > 0) metadataBuilder.append(" | ");
            metadataBuilder.append("类型: ").append(getMessageTypeName(typeObj));
        }

        return metadataBuilder.toString();
    }

    /**
     * 格式化时间
     */
    private String formatTime(String timeStr) {
        try {
            // 尝试解析时间戳
            long timestamp = Long.parseLong(timeStr);
            return TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp));
        } catch (NumberFormatException e) {
            // 如果不是时间戳，直接返回原字符串
            return timeStr;
        }
    }

    /**
     * 获取消息类型名称
     */
    private String getMessageTypeName(Object type) {
        if (type == null) {
            return "未知";
        }

        try {
            Integer typeInt = (Integer) type;
            return switch (typeInt) {
                case 1 -> "文本";
                case 3 -> "图片";
                case 47 -> "表情";
                case 49 -> "引用";
                default -> "其他(" + typeInt + ")";
            };
        } catch (ClassCastException e) {
            return type.toString();
        }
    }
}