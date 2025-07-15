package com.example.provider.aspect;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.Map;

import org.apache.dubbo.rpc.RpcContext;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class HaiServiceAspect {
    // 从配置文件中读取应用程序名称
    @Value("${spring.application.name}")
    private String applicationName;
    @Value("${user.sys}")
    private String sys;

    // 定义切点，拦截 HaiServiceImpl 的所有方法
    @Pointcut("execution(* com.example.provider.service.impl.HaiServiceImpl.*(..))")
    public void HaiServiceMethods() {
    }

    // 在方法执行前添加统一的 Tag
    @Before("HaiServiceMethods()")
    public void addCommonTag() {
        ActiveSpan.tag("dc", "F"); // 添加 Tag：dc=F
    }

    // 在方法执行后进行统一处理，不会影响交易响应速度
    @AfterReturning(pointcut = "HaiServiceMethods()", returning = "result")
    public void afterMethod(JoinPoint joinPoint, Object result) {
        addTags(joinPoint);

        // 写入方法执行结果
        if (result != null) {
            ActiveSpan.tag("method.result", result.toString());
        }
    }

    private void addTags(JoinPoint joinPoint) {
        RpcContext context = RpcContext.getContext();
        Map<String, String> envVars = System.getenv();
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            ActiveSpan.tag("env." + entry.getKey(), entry.getValue());
        }
        if (context != null) {
            // 写入 RpcContext 的基本信息
            ActiveSpan.tag("dubbo.remote_host", context.getRemoteHost());
            ActiveSpan.tag("dubbo.remote_port", String.valueOf(context.getRemotePort()));
            ActiveSpan.tag("dubbo.local_host", context.getLocalHost());
            context.getObjectAttachments().forEach((key, value) -> {
                ActiveSpan.tag("rpc.context." + key, value != null ? value.toString() : "null");
            });
            // 遍历方法参数
            Object[] arguments = context.getArguments();
            if (arguments != null) {
                for (int i = 0; i < arguments.length; i++) {
                    ActiveSpan.tag("rpc.argument[" + i + "]", arguments[i] != null ? arguments[i].toString() : "null");
                }
            }
            // 写入方法和接口信息
            if (context.getUrl() != null) {
                ActiveSpan.tag("rpc.service_interface",
                        context.getUrl().getServiceInterface() != null ? context.getUrl().getServiceInterface()
                                : "unknown");
                ActiveSpan.tag("rpc.method_name",
                        context.getMethodName() != null ? context.getMethodName() : "unknown");
                ActiveSpan.tag("rpc.service_url",
                        context.getUrl().toFullString() != null ? context.getUrl().toFullString() : "unknown");
                ActiveSpan.tag("rpc.protocol",
                        context.getUrl().getProtocol() != null ? context.getUrl().getProtocol() : "unknown");
            }

            // 写入调用链路信息
            ActiveSpan.tag("rpc.role", context.isProviderSide() ? "provider" : "consumer");
            context.getObjectAttachments().forEach((key, value) -> {
                ActiveSpan.tag("rpc.object_attachment." + key, value != null ? value.toString() : "null");
            });

        } else {
            ActiveSpan.info("RpcContext is null.");
        }

        // 写入应用程序名称和系统信息
        ActiveSpan.tag("application.name", applicationName);
        ActiveSpan.tag("sys", sys);

        // 获取 JVM 运行时信息并写入 Tags
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();

        // JVM 内存使用情况
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        ActiveSpan.tag("jvm.heap.init.type.Int64", String.valueOf(heapMemoryUsage.getInit()));
        ActiveSpan.tag("jvm.heap.used.type.Int64", String.valueOf(heapMemoryUsage.getUsed()));
        ActiveSpan.tag("jvm.heap.committed.type.Int64", String.valueOf(heapMemoryUsage.getCommitted()));
        ActiveSpan.tag("jvm.heap.max.type.Int64", String.valueOf(heapMemoryUsage.getMax()));
        ActiveSpan.tag("jvm.nonheap.init.type.Int64", String.valueOf(nonHeapMemoryUsage.getInit()));
        ActiveSpan.tag("jvm.nonheap.used.type.Int64", String.valueOf(nonHeapMemoryUsage.getUsed()));
        ActiveSpan.tag("jvm.nonheap.committed.type.Int64", String.valueOf(nonHeapMemoryUsage.getCommitted()));
        ActiveSpan.tag("jvm.nonheap.max.type.Int64", String.valueOf(nonHeapMemoryUsage.getMax()));

        // JVM 运行时信息
        ActiveSpan.tag("jvm.name", runtimeMXBean.getVmName());
        ActiveSpan.tag("jvm.vendor", runtimeMXBean.getVmVendor());
        ActiveSpan.tag("jvm.version", runtimeMXBean.getVmVersion());
        ActiveSpan.tag("jvm.start_time", String.valueOf(runtimeMXBean.getStartTime()));
        ActiveSpan.tag("jvm.uptime", runtimeMXBean.getUptime() + " ms");

        // 操作系统信息
        ActiveSpan.tag("os.name", osMXBean.getName());
        ActiveSpan.tag("os.arch", osMXBean.getArch());
        ActiveSpan.tag("os.version", osMXBean.getVersion());

        // 获取线程数量
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        // 获取线程相关信息
        // 当前线程数量
        int threadCount = threadMXBean.getThreadCount();
        ActiveSpan.tag("thread.count.type.Int64", String.valueOf(threadCount));

        // 峰值线程数量
        int peakThreadCount = threadMXBean.getPeakThreadCount();
        ActiveSpan.tag("thread.peak_count.type.Int64", String.valueOf(peakThreadCount));

        // 守护线程数量
        int daemonThreadCount = threadMXBean.getDaemonThreadCount();
        ActiveSpan.tag("thread.daemon_count.type.Int64", String.valueOf(daemonThreadCount));

        // 总线程启动数量
        long totalStartedThreadCount = threadMXBean.getTotalStartedThreadCount();
        ActiveSpan.tag("thread.total_started_count.type.Int64", String.valueOf(totalStartedThreadCount));

        // 当前线程 CPU 时间（如果支持）
        if (threadMXBean.isCurrentThreadCpuTimeSupported()) {
            long currentThreadCpuTime = threadMXBean.getCurrentThreadCpuTime();
            ActiveSpan.tag("thread.current_cpu_time.type.Int64", String.valueOf(currentThreadCpuTime));
        }

        // 当前线程用户时间（如果支持）
        if (threadMXBean.isCurrentThreadCpuTimeSupported()) {
            long currentThreadUserTime = threadMXBean.getCurrentThreadUserTime();
            ActiveSpan.tag("thread.current_user_time.type.Int64", String.valueOf(currentThreadUserTime));
        }
    }

    // 在方法抛出异常后进行处理
    @AfterThrowing(pointcut = "HaiServiceMethods()", throwing = "exception")
    public void afterThrowing(JoinPoint joinPoint, Throwable exception) {
        addTags(joinPoint);
        String methodName = joinPoint.getSignature().getName();
        ActiveSpan.error("Method " + methodName + " threw an exception: " + exception.getMessage());
    }
}