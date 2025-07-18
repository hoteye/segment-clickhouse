package com.example.provider.aspect;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.util.Map;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class HelloServiceAspect {

    private static final Logger logger = LoggerFactory.getLogger(HelloServiceAspect.class);

    // 从配置文件中读取应用程序名称
    @Value("${spring.application.name}")
    private String applicationName;
    @Value("${user.sys}")
    private String sys;

    @Autowired
    private MeterRegistry meterRegistry;

    // 配置开关
    @Value("${customtags.enable-env:true}")
    private boolean enableEnv;
    @Value("${customtags.enable-rpc:true}")
    private boolean enableRpc;
    @Value("${customtags.enable-app:true}")
    private boolean enableApp;
    @Value("${customtags.enable-jvm:true}")
    private boolean enableJvm;
    @Value("${customtags.enable-os:true}")
    private boolean enableOs;
    @Value("${customtags.enable-thread:true}")
    private boolean enableThread;

    @PostConstruct
    public void init() {
        System.out.println("applicationName: " + applicationName);
        System.out.println("sys: " + sys);
    }

    // 定义切点，拦截 HelloServiceImpl 的所有方法
    @Pointcut("execution(* com.example.provider.service.impl.HelloServiceImpl.*(..))")
    public void helloServiceMethods() {
    }

    // 在方法执行前添加统一的 Tag
    @Before("helloServiceMethods()")
    public void beforeMethod(JoinPoint joinPoint) throws InterruptedException {
        // 统一采集访问量指标
        String methodName = joinPoint.getSignature().getName();
        meterRegistry.counter("dubbo_provider_method_count", "method", methodName).increment();
        ActiveSpan.tag("dc", "F"); // 添加 Tag：dc=F
        ActiveSpan.tag("beforeMethod", "F");// 无效的tag，span不支持在before方法中添加tag
    }

    // 在方法执行后进行统一处理，不会影响交易响应速度
    @AfterReturning(pointcut = "helloServiceMethods()", returning = "result")
    public void afterMethod(JoinPoint joinPoint, Object result) {
        addTags(joinPoint);

        // 写入方法执行结果
        if (result != null) {
            ActiveSpan.tag("method.result", result.toString());
        }
    }

    private void addTags(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        double current = meterRegistry.counter("dubbo_provider_method_count", "method", methodName).count();
        ActiveSpan.tag("method.access_count_type_int64", String.valueOf((long) current));

        // 环境变量
        if (enableEnv) {
            Map<String, String> envVars = System.getenv();
            for (Map.Entry<String, String> entry : envVars.entrySet()) {
                ActiveSpan.tag("env." + entry.getKey(), entry.getValue());
            }
        }

        // RpcContext
        if (enableRpc) {
            RpcContext context = RpcContext.getContext();
            if (context != null) {
                ActiveSpan.tag("dubbo.remote_host", context.getRemoteHost());
                ActiveSpan.tag("dubbo.remote_port", String.valueOf(context.getRemotePort()));
                ActiveSpan.tag("dubbo.local_host", context.getLocalHost());
                context.getObjectAttachments().forEach((key, value) -> {
                    ActiveSpan.tag("rpc.context." + key, value != null ? value.toString() : "null");
                });
                Object[] arguments = context.getArguments();
                if (arguments != null) {
                    for (int i = 0; i < arguments.length; i++) {
                        ActiveSpan.tag("rpc.argument[" + i + "]",
                                arguments[i] != null ? arguments[i].toString() : "null");
                    }
                }
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
                ActiveSpan.tag("rpc.role", context.isProviderSide() ? "provider" : "consumer");
                context.getObjectAttachments().forEach((key, value) -> {
                    ActiveSpan.tag("rpc.object_attachment." + key, value != null ? value.toString() : "null");
                });
            } else {
                ActiveSpan.info("RpcContext is null.");
            }
        }

        // 应用信息
        if (enableApp) {
            ActiveSpan.tag("application.name", applicationName);
            ActiveSpan.tag("sys", sys);
        }

        // JVM 信息
        if (enableJvm) {
            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
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
            ActiveSpan.tag("jvm.name", runtimeMXBean.getVmName());
            ActiveSpan.tag("jvm.vendor", runtimeMXBean.getVmVendor());
            ActiveSpan.tag("jvm.version", runtimeMXBean.getVmVersion());
            ActiveSpan.tag("jvm.start_time", String.valueOf(runtimeMXBean.getStartTime()));
            ActiveSpan.tag("jvm.uptime", runtimeMXBean.getUptime() + " ms");
            
            // GC 信息
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            long totalGcTime = 0;
            long totalGcCollections = 0;
            
            for (GarbageCollectorMXBean gcBean : gcBeans) {
                long collectionTime = gcBean.getCollectionTime();
                long collectionCount = gcBean.getCollectionCount();
                String gcName = gcBean.getName().replaceAll("\\s+", "_").toLowerCase();
                
                ActiveSpan.tag("gc." + gcName + ".time.type.Int64", String.valueOf(collectionTime));
                ActiveSpan.tag("gc." + gcName + ".count.type.Int64", String.valueOf(collectionCount));
                
                totalGcTime += collectionTime;
                totalGcCollections += collectionCount;
            }
            
            ActiveSpan.tag("gc.total_time.type.Int64", String.valueOf(totalGcTime));
            ActiveSpan.tag("gc.total_collections.type.Int64", String.valueOf(totalGcCollections));
        }

        // 操作系统信息
        if (enableOs) {
            OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
            ActiveSpan.tag("os.name", osMXBean.getName());
            ActiveSpan.tag("os.arch", osMXBean.getArch());
            ActiveSpan.tag("os.version", osMXBean.getVersion());
        }

        // 线程信息
        if (enableThread) {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            int threadCount = threadMXBean.getThreadCount();
            ActiveSpan.tag("thread.count.type.Int64", String.valueOf(threadCount));
            int peakThreadCount = threadMXBean.getPeakThreadCount();
            ActiveSpan.tag("thread.peak_count.type.Int64", String.valueOf(peakThreadCount));
            int daemonThreadCount = threadMXBean.getDaemonThreadCount();
            ActiveSpan.tag("thread.daemon_count.type.Int64", String.valueOf(daemonThreadCount));
            long totalStartedThreadCount = threadMXBean.getTotalStartedThreadCount();
            ActiveSpan.tag("thread.total_started_count.type.Int64", String.valueOf(totalStartedThreadCount));
            if (threadMXBean.isCurrentThreadCpuTimeSupported()) {
                long currentThreadCpuTime = threadMXBean.getCurrentThreadCpuTime();
                ActiveSpan.tag("thread.current_cpu_time.type.Int64", String.valueOf(currentThreadCpuTime));
                long currentThreadUserTime = threadMXBean.getCurrentThreadUserTime();
                ActiveSpan.tag("thread.current_user_time.type.Int64", String.valueOf(currentThreadUserTime));
            }
        }
    }

    // 在方法抛出异常后进行处理
    @AfterThrowing(pointcut = "helloServiceMethods()", throwing = "exception")
    public void afterThrowing(JoinPoint joinPoint, Throwable exception) {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        logger.error("方法 {} 抛出异常，参数: {}, 异常信息: {}",
                methodName,
                args != null ? java.util.Arrays.toString(args) : "null",
                exception.getMessage(),
                exception);

        // 记录到SkyWalking span
        ActiveSpan.error(exception);
        ActiveSpan.tag("error.method", methodName);
        ActiveSpan.tag("error.type", exception.getClass().getSimpleName());
        ActiveSpan.tag("error.message", exception.getMessage());
        if (args != null && args.length > 0) {
            ActiveSpan.tag("error.parameters", java.util.Arrays.toString(args));
        }

        // 添加其他标签
        addTags(joinPoint);
    }
}