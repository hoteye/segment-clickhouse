package com.example.consumer.util;

import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 错误日志工具类，用于统一处理错误日志和SkyWalking span关联
 */
public class ErrorLogUtil {

    /**
     * 记录错误日志并关联到SkyWalking span
     * 
     * @param logger    日志记录器
     * @param message   错误消息
     * @param throwable 异常对象
     * @param context   上下文信息
     */
    public static void logError(Logger logger, String message, Throwable throwable, String context) {
        logError(logger, message, throwable, context, null);
    }

    /**
     * 记录错误日志并关联到SkyWalking span
     * 
     * @param logger         日志记录器
     * @param message        错误消息
     * @param throwable      异常对象
     * @param context        上下文信息
     * @param additionalTags 额外的标签信息
     */
    public static void logError(Logger logger, String message, Throwable throwable, String context,
            java.util.Map<String, String> additionalTags) {
        // 记录到日志
        logger.error("{} - 上下文: {}, 错误: {}", message, context, throwable.getMessage(), throwable);

        // 记录到SkyWalking span
        ActiveSpan.error(throwable);
        ActiveSpan.tag("error.context", context);
        ActiveSpan.tag("error.type", throwable.getClass().getSimpleName());
        ActiveSpan.tag("error.message", throwable.getMessage());

        // 添加额外的标签
        if (additionalTags != null) {
            additionalTags.forEach(ActiveSpan::tag);
        }

        // 记录堆栈信息（可选，用于调试）
        if (logger.isDebugEnabled()) {
            ActiveSpan.tag("error.stack_trace", getStackTraceString(throwable));
        }
    }

    /**
     * 记录警告日志并关联到SkyWalking span
     * 
     * @param logger  日志记录器
     * @param message 警告消息
     * @param context 上下文信息
     */
    public static void logWarning(Logger logger, String message, String context) {
        logger.warn("{} - 上下文: {}", message, context);

        ActiveSpan.tag("warning.context", context);
        ActiveSpan.tag("warning.message", message);
    }

    /**
     * 记录业务异常
     * 
     * @param logger       日志记录器
     * @param businessCode 业务代码
     * @param message      错误消息
     * @param context      上下文信息
     */
    public static void logBusinessError(Logger logger, String businessCode, String message, String context) {
        logger.error("业务异常 - 代码: {}, 消息: {}, 上下文: {}", businessCode, message, context);

        ActiveSpan.tag("business.error.code", businessCode);
        ActiveSpan.tag("business.error.message", message);
        ActiveSpan.tag("business.error.context", context);
    }

    /**
     * 获取异常的堆栈跟踪字符串
     * 
     * @param throwable 异常对象
     * @return 堆栈跟踪字符串
     */
    private static String getStackTraceString(Throwable throwable) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}