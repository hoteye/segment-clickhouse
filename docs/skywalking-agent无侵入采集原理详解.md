# SkyWalking Java Agent无侵入采集运行信息的实现原理

## 一、SkyWalking Agent的核心技术原理

### 1.1 字节码增强技术（Bytecode Enhancement）

SkyWalking Agent的核心是基于**字节码增强技术**，在JVM类加载时动态修改类的字节码，实现无侵入的数据采集。

#### 1.1.1 技术原理
```java
// SkyWalking Agent工作原理示意
public class BytecodeEnhancer {
    // 在类加载时被调用
    public static byte[] transform(ClassLoader loader, String className, 
                                  Class<?> classBeingRedefined, 
                                  ProtectionDomain protectionDomain, 
                                  byte[] classfileBuffer) {
        // 1. 判断是否需要增强
        if (!shouldEnhance(className)) {
            return classfileBuffer;
        }
        // 2. 解析原始字节码
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(classfileBuffer);
        classReader.accept(classNode, 0);
        // 3. 插入监控代码
        insertMonitoringCode(classNode);
        // 4. 生成新的字节码
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);
        return classWriter.toByteArray();
    }
}
```

#### 1.1.2 实际增强示例
**原始业务代码**：
```java
@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;
    public Order createOrder(OrderRequest request) {
        // 纯业务逻辑，无任何监控代码
        return orderRepository.save(request);
    }
}
```
**增强后的字节码**（伪代码）：
```java
@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;
    public Order createOrder(OrderRequest request) {
        // SkyWalking自动插入的监控代码
        Span span = ContextManager.createLocalSpan("OrderService/createOrder");
        span.setComponent("Spring");
        span.setLayer(SpanLayer.SOFA);
        try {
            // 设置业务标签
            span.tag("user.id", request.getUserId());
            span.tag("order.amount", request.getAmount());
            // 原始业务逻辑
            Order result = orderRepository.save(request);
            // 设置成功状态
            span.setStatus(Status.OK);
            return result;
        } catch (Exception e) {
            // 设置错误状态
            span.setStatus(Status.ERROR);
            span.log(e);
            throw e;
        } finally {
            // 结束Span
            ContextManager.stopSpan();
        }
    }
}
```

## 二、各类运行信息的无侵入采集实现

### 2.1 JVM信息采集

#### 2.1.1 实现原理
通过拦截JVM的JMX接口和系统调用，自动采集JVM运行信息。

```java
// JVM信息采集插件
public class JVMInfoPlugin {
    private static final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    private static final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private static final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, 
                           Object[] allArguments, Class<?>[] argumentsTypes, 
                           MethodInterceptResult result) throws Throwable {
        // 自动采集JVM信息
        collectJVMInfo();
    }
    private void collectJVMInfo() {
        // 获取JVM基本信息
        String jvmVersion = System.getProperty("java.version");
        String jvmVendor = System.getProperty("java.vendor");
        long startTime = runtimeMXBean.getStartTime();
        // 获取内存信息
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        // 获取线程信息
        int threadCount = threadMXBean.getThreadCount();
        int daemonThreadCount = threadMXBean.getDaemonThreadCount();
        // 获取GC信息
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        // 设置到Span中
        Span span = ContextManager.activeSpan();
        span.tag("jvm.version", jvmVersion);
        span.tag("jvm.heap.used", String.valueOf(heapMemoryUsage.getUsed()));
        span.tag("jvm.heap.max", String.valueOf(heapMemoryUsage.getMax()));
        span.tag("jvm.thread.count", String.valueOf(threadCount));
        span.tag("jvm.gc.count", String.valueOf(getGCCount(gcBeans)));
    }
}
```

#### 2.1.2 字节码增强点
```java
// 在关键方法调用时自动插入JVM信息采集
public class JVMEnhancer {
    public static void enhanceMethod(MethodNode methodNode) {
        // 在方法开始时插入JVM信息采集代码
        InsnList instructions = methodNode.instructions;
        // 插入调用JVMInfoCollector.collect()的字节码
        instructions.insert(new MethodInsnNode(
            INVOKESTATIC, 
            "org/apache/skywalking/apm/agent/core/jvm/JVMInfoCollector", 
            "collect", 
            "()V", 
            false
        ));
    }
}
```

### 2.2 进程和线程信息采集

#### 2.2.1 进程信息采集
```java
public class ProcessInfoPlugin {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, 
                           Object[] allArguments, Class<?>[] argumentsTypes, 
                           MethodInterceptResult result) throws Throwable {
        // 获取进程信息
        String pid = getProcessId();
        String processName = getProcessName();
        long startTime = System.currentTimeMillis();
        // 设置到Span中
        Span span = ContextManager.activeSpan();
        span.tag("process.id", pid);
        span.tag("process.name", processName);
        span.tag("process.start.time", String.valueOf(startTime));
    }
    private String getProcessId() {
        try {
            // 通过JMX获取进程ID
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            return runtimeName.split("@")[0];
        } catch (Exception e) {
            return "unknown";
        }
    }
}
```

#### 2.2.2 线程信息采集
```java
public class ThreadInfoPlugin {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, 
                           Object[] allArguments, Class<?>[] argumentsTypes, 
                           MethodInterceptResult result) throws Throwable {
        Thread currentThread = Thread.currentThread();
        // 获取线程信息
        long threadId = currentThread.getId();
        String threadName = currentThread.getName();
        Thread.State threadState = currentThread.getState();
        int threadPriority = currentThread.getPriority();
        // 设置到Span中
        Span span = ContextManager.activeSpan();
        span.tag("thread.id", String.valueOf(threadId));
        span.tag("thread.name", threadName);
        span.tag("thread.state", threadState.name());
        span.tag("thread.priority", String.valueOf(threadPriority));
    }
}
```

### 2.3 数据库连接池信息采集

#### 2.3.1 HikariCP连接池监控
```java
// HikariCP连接池插件
public class HikariCPPlugin {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, 
                           Object[] allArguments, Class<?>[] argumentsTypes, 
                           MethodInterceptResult result) throws Throwable {
        if (objInst instanceof HikariDataSource) {
            HikariDataSource dataSource = (HikariDataSource) objInst;
            HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
            // 获取连接池信息
            int totalConnections = poolMXBean.getTotalConnections();
            int activeConnections = poolMXBean.getActiveConnections();
            int idleConnections = poolMXBean.getIdleConnections();
            int threadsAwaitingConnection = poolMXBean.getThreadsAwaitingConnection();
            // 设置到Span中
            Span span = ContextManager.activeSpan();
            span.tag("db.pool.total", String.valueOf(totalConnections));
            span.tag("db.pool.active", String.valueOf(activeConnections));
            span.tag("db.pool.idle", String.valueOf(idleConnections));
            span.tag("db.pool.waiting", String.valueOf(threadsAwaitingConnection));
        }
    }
}
```

#### 2.3.2 Druid连接池监控
```java
// Druid连接池插件
public class DruidPlugin {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, 
                           Object[] allArguments, Class<?>[] argumentsTypes, 
                           MethodInterceptResult result) throws Throwable {
        if (objInst instanceof DruidDataSource) {
            DruidDataSource dataSource = (DruidDataSource) objInst;
            // 获取连接池信息
            int activeCount = dataSource.getActiveCount();
            int poolCount = dataSource.getPoolCount();
            int maxActive = dataSource.getMaxActive();
            long waitThreadCount = dataSource.getWaitThreadCount();
            // 设置到Span中
            Span span = ContextManager.activeSpan();
            span.tag("db.pool.active", String.valueOf(activeCount));
            span.tag("db.pool.total", String.valueOf(poolCount));
            span.tag("db.pool.max", String.valueOf(maxActive));
            span.tag("db.pool.waiting", String.valueOf(waitThreadCount));
        }
    }
}
```

### 2.4 线程池信息采集

#### 2.4.1 ThreadPoolExecutor监控
```java
// 线程池监控插件
public class ThreadPoolPlugin {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, 
                           Object[] allArguments, Class<?>[] argumentsTypes, 
                           MethodInterceptResult result) throws Throwable {
        if (objInst instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor executor = (ThreadPoolExecutor) objInst;
            // 获取线程池信息
            int corePoolSize = executor.getCorePoolSize();
            int maximumPoolSize = executor.getMaximumPoolSize();
            int activeCount = executor.getActiveCount();
            long completedTaskCount = executor.getCompletedTaskCount();
            int queueSize = executor.getQueue().size();
            // 设置到Span中
            Span span = ContextManager.activeSpan();
            span.tag("thread.pool.core", String.valueOf(corePoolSize));
            span.tag("thread.pool.max", String.valueOf(maximumPoolSize));
            span.tag("thread.pool.active", String.valueOf(activeCount));
            span.tag("thread.pool.completed", String.valueOf(completedTaskCount));
            span.tag("thread.pool.queue.size", String.valueOf(queueSize));
        }
    }
}
```

### 2.5 业务ID采集

#### 2.5.1 HTTP请求中的业务ID
```java
// HTTP请求插件
public class HttpRequestPlugin {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, 
                           Object[] allArguments, Class<?>[] argumentsTypes, 
                           MethodInterceptResult result) throws Throwable {
        if (allArguments[0] instanceof HttpServletRequest) {
            HttpServletRequest request = (HttpServletRequest) allArguments[0];
            // 从请求头中提取业务ID
            String userId = request.getHeader("X-User-ID");
            String orderId = request.getHeader("X-Order-ID");
            String traceId = request.getHeader("X-Trace-ID");
            // 从请求参数中提取业务ID
            String requestUserId = request.getParameter("userId");
            String requestOrderId = request.getParameter("orderId");
            // 设置到Span中
            Span span = ContextManager.activeSpan();
            if (userId != null) span.tag("user.id", userId);
            if (orderId != null) span.tag("order.id", orderId);
            if (traceId != null) span.tag("trace.id", traceId);
            if (requestUserId != null) span.tag("request.user.id", requestUserId);
            if (requestOrderId != null) span.tag("request.order.id", requestOrderId);
        }
    }
}
```

#### 2.5.2 RPC调用中的业务ID
```java
// Dubbo RPC插件
public class DubboPlugin {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, 
                           Object[] allArguments, Class<?>[] argumentsTypes, 
                           MethodInterceptResult result) throws Throwable {
        if (allArguments[0] instanceof Invocation) {
            Invocation invocation = (Invocation) allArguments[0];
            // 从RPC上下文获取业务ID
            RpcContext rpcContext = RpcContext.getContext();
            String userId = rpcContext.getAttachment("userId");
            String orderId = rpcContext.getAttachment("orderId");
            // 从方法参数中提取业务ID
            Object[] arguments = invocation.getArguments();
            if (arguments != null && arguments.length > 0) {
                // 通过反射获取参数中的业务ID
                extractBusinessIdFromArguments(arguments);
            }
            // 设置到Span中
            Span span = ContextManager.activeSpan();
            if (userId != null) span.tag("user.id", userId);
            if (orderId != null) span.tag("order.id", orderId);
        }
    }
    private void extractBusinessIdFromArguments(Object[] arguments) {
        for (Object arg : arguments) {
            if (arg != null) {
                // 通过反射获取对象中的业务ID字段
                try {
                    Field userIdField = arg.getClass().getDeclaredField("userId");
                    userIdField.setAccessible(true);
                    Object userId = userIdField.get(arg);
                    if (userId != null) {
                        ContextManager.activeSpan().tag("user.id", userId.toString());
                    }
                } catch (Exception e) {
                    // 忽略异常，继续处理其他字段
                }
            }
        }
    }
}
```

### 2.6 环境变量采集

#### 2.6.1 采集方式
SkyWalking Agent在启动时自动采集所有环境变量，并可通过配置选择性上报。

```java
public class EnvVarPlugin {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method,
                             Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        Map<String, String> envVars = System.getenv();
        Span span = ContextManager.activeSpan();
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            // 只采集白名单中的环境变量，避免敏感信息泄露
            if (isWhitelisted(entry.getKey())) {
                span.tag("env." + entry.getKey(), entry.getValue());
            }
        }
    }
    private boolean isWhitelisted(String key) {
        // 可配置的白名单
        return key.startsWith("JAVA_") || key.equals("HOSTNAME") || key.equals("POD_NAME");
    }
}
```

### 2.7 Dubbo信息采集

#### 2.7.1 插件机制
SkyWalking内置Dubbo插件，通过拦截Dubbo的Invoker和Filter链，自动采集服务名、方法名、参数、调用链等信息。

```java
public class DubboInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                             Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        Invocation invocation = (Invocation) allArguments[0];
        String service = invocation.getInvoker().getInterface().getName();
        String methodName = invocation.getMethodName();
        Span span = ContextManager.createEntrySpan(service + "/" + methodName, null);
        span.tag("dubbo.service", service);
        span.tag("dubbo.method", methodName);
        // 采集参数、上下文等
    }
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }
    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }
}
```

### 2.8 HTTP信息采集

#### 2.8.1 Servlet/Spring MVC自动采集
SkyWalking通过拦截Servlet Filter、Spring MVC Handler等入口，自动采集HTTP请求的URL、方法、状态码、响应时间等信息。

```java
public class HttpServletInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                             Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        HttpServletRequest request = (HttpServletRequest) allArguments[0];
        String url = request.getRequestURI();
        String methodType = request.getMethod();
        Span span = ContextManager.createEntrySpan(url, null);
        span.tag("http.method", methodType);
        span.tag("http.url", url);
    }
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
        HttpServletResponse response = (HttpServletResponse) allArguments[1];
        int status = response.getStatus();
        ContextManager.activeSpan().tag("http.status_code", String.valueOf(status));
        ContextManager.stopSpan();
        return ret;
    }
    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }
}
```

---

## 三、无侵入采集的优势与配置灵活性

- **零代码改动**：所有采集逻辑均由Agent和插件自动完成，业务系统无需任何代码修改。
- **插件化机制**：SkyWalking Agent支持按需加载插件，自动适配不同中间件和框架。
- **配置灵活**：通过`agent.config`和插件配置文件，可灵活控制采集内容、采样率、白名单等。
- **性能可控**：字节码增强和异步上报机制，保证对业务系统性能影响极小。
- **动态开关**：可通过配置或环境变量动态开启/关闭特定采集项，满足不同系统和场景需求。

---

## 四、应用容器运行信息自动化采集

### 4.1 容器环境感知与自动适配

SkyWalking Agent具备**容器环境自动感知**能力，能够自动识别Docker、Kubernetes等容器环境，并采集容器相关的运行信息。

#### 4.1.1 容器环境检测
```java
public class ContainerEnvironmentDetector {
    private static final String CGROUP_PATH = "/proc/self/cgroup";
    private static final String DOCKER_ENV_FILE = "/.dockerenv";
    private static final String K8S_SERVICE_HOST = "KUBERNETES_SERVICE_HOST";
    
    public static ContainerInfo detectContainerEnvironment() {
        ContainerInfo info = new ContainerInfo();
        
        // 检测Docker环境
        if (isDockerContainer()) {
            info.setContainerType("docker");
            info.setContainerId(extractDockerContainerId());
        }
        
        // 检测Kubernetes环境
        if (isKubernetesPod()) {
            info.setContainerType("kubernetes");
            info.setPodName(System.getenv("POD_NAME"));
            info.setPodNamespace(System.getenv("POD_NAMESPACE"));
            info.setPodIp(System.getenv("POD_IP"));
            info.setNodeName(System.getenv("NODE_NAME"));
        }
        
        return info;
    }
    
    private static boolean isDockerContainer() {
        return new File(DOCKER_ENV_FILE).exists();
    }
    
    private static boolean isKubernetesPod() {
        return System.getenv(K8S_SERVICE_HOST) != null;
    }
    
    private static String extractDockerContainerId() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(CGROUP_PATH));
            for (String line : lines) {
                if (line.contains("docker")) {
                    // 提取容器ID
                    String[] parts = line.split("/");
                    if (parts.length > 2) {
                        return parts[parts.length - 1];
                    }
                }
            }
        } catch (IOException e) {
            // 忽略异常
        }
        return "unknown";
    }
}
```

### 4.2 容器资源使用情况采集

#### 4.2.1 CPU使用率采集
```java
public class ContainerCPUPlugin {
    private static final String CPU_STAT_FILE = "/proc/stat";
    private static final String CGROUP_CPU_STAT = "/sys/fs/cgroup/cpu/cpu.stat";
    
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method,
                             Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        // 采集容器CPU使用情况
        ContainerCPUInfo cpuInfo = collectCPUInfo();
        
        Span span = ContextManager.activeSpan();
        span.tag("container.cpu.usage", String.valueOf(cpuInfo.getCpuUsage()));
        span.tag("container.cpu.limit", String.valueOf(cpuInfo.getCpuLimit()));
        span.tag("container.cpu.quota", String.valueOf(cpuInfo.getCpuQuota()));
        span.tag("container.cpu.period", String.valueOf(cpuInfo.getCpuPeriod()));
    }
    
    private ContainerCPUInfo collectCPUInfo() {
        ContainerCPUInfo info = new ContainerCPUInfo();
        
        // 从cgroup获取CPU配额信息
        try {
            String cpuQuota = Files.readString(Paths.get("/sys/fs/cgroup/cpu/cpu.cfs_quota_us")).trim();
            String cpuPeriod = Files.readString(Paths.get("/sys/fs/cgroup/cpu/cpu.cfs_period_us")).trim();
            
            info.setCpuQuota(Long.parseLong(cpuQuota));
            info.setCpuPeriod(Long.parseLong(cpuPeriod));
            
            // 计算CPU限制
            if (info.getCpuQuota() > 0) {
                info.setCpuLimit((double) info.getCpuQuota() / info.getCpuPeriod());
            }
            
            // 计算CPU使用率
            info.setCpuUsage(calculateCPUUsage());
            
        } catch (IOException e) {
            // 容器外环境，忽略
        }
        
        return info;
    }
    
    private double calculateCPUUsage() {
        // 通过读取/proc/stat计算CPU使用率
        try {
            List<String> lines = Files.readAllLines(Paths.get(CPU_STAT_FILE));
            String cpuLine = lines.get(0);
            String[] values = cpuLine.split("\\s+");
            
            long user = Long.parseLong(values[1]);
            long nice = Long.parseLong(values[2]);
            long system = Long.parseLong(values[3]);
            long idle = Long.parseLong(values[4]);
            
            long total = user + nice + system + idle;
            long used = user + nice + system;
            
            return (double) used / total * 100;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
```

#### 4.2.2 内存使用情况采集
```java
public class ContainerMemoryPlugin {
    private static final String MEMORY_STAT_FILE = "/sys/fs/cgroup/memory/memory.stat";
    private static final String MEMORY_USAGE_FILE = "/sys/fs/cgroup/memory/memory.usage_in_bytes";
    private static final String MEMORY_LIMIT_FILE = "/sys/fs/cgroup/memory/memory.limit_in_bytes";
    
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method,
                             Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        // 采集容器内存使用情况
        ContainerMemoryInfo memoryInfo = collectMemoryInfo();
        
        Span span = ContextManager.activeSpan();
        span.tag("container.memory.usage", String.valueOf(memoryInfo.getMemoryUsage()));
        span.tag("container.memory.limit", String.valueOf(memoryInfo.getMemoryLimit()));
        span.tag("container.memory.rss", String.valueOf(memoryInfo.getRss()));
        span.tag("container.memory.cache", String.valueOf(memoryInfo.getCache()));
        span.tag("container.memory.swap", String.valueOf(memoryInfo.getSwap()));
        span.tag("container.memory.usage.percent", String.valueOf(memoryInfo.getUsagePercent()));
    }
    
    private ContainerMemoryInfo collectMemoryInfo() {
        ContainerMemoryInfo info = new ContainerMemoryInfo();
        
        try {
            // 读取内存使用量
            String memoryUsage = Files.readString(Paths.get(MEMORY_USAGE_FILE)).trim();
            info.setMemoryUsage(Long.parseLong(memoryUsage));
            
            // 读取内存限制
            String memoryLimit = Files.readString(Paths.get(MEMORY_LIMIT_FILE)).trim();
            info.setMemoryLimit(Long.parseLong(memoryLimit));
            
            // 读取详细内存统计
            List<String> lines = Files.readAllLines(Paths.get(MEMORY_STAT_FILE));
            for (String line : lines) {
                String[] parts = line.split("\\s+");
                if (parts.length == 2) {
                    switch (parts[0]) {
                        case "rss":
                            info.setRss(Long.parseLong(parts[1]));
                            break;
                        case "cache":
                            info.setCache(Long.parseLong(parts[1]));
                            break;
                        case "swap":
                            info.setSwap(Long.parseLong(parts[1]));
                            break;
                    }
                }
            }
            
            // 计算使用百分比
            if (info.getMemoryLimit() > 0) {
                info.setUsagePercent((double) info.getMemoryUsage() / info.getMemoryLimit() * 100);
            }
            
        } catch (IOException e) {
            // 容器外环境，忽略
        }
        
        return info;
    }
}
```

#### 4.2.3 网络使用情况采集
```java
public class ContainerNetworkPlugin {
    private static final String NETWORK_STAT_FILE = "/proc/net/dev";
    
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method,
                             Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        // 采集容器网络使用情况
        ContainerNetworkInfo networkInfo = collectNetworkInfo();
        
        Span span = ContextManager.activeSpan();
        span.tag("container.network.rx.bytes", String.valueOf(networkInfo.getRxBytes()));
        span.tag("container.network.tx.bytes", String.valueOf(networkInfo.getTxBytes()));
        span.tag("container.network.rx.packets", String.valueOf(networkInfo.getRxPackets()));
        span.tag("container.network.tx.packets", String.valueOf(networkInfo.getTxPackets()));
        span.tag("container.network.rx.errors", String.valueOf(networkInfo.getRxErrors()));
        span.tag("container.network.tx.errors", String.valueOf(networkInfo.getTxErrors()));
    }
    
    private ContainerNetworkInfo collectNetworkInfo() {
        ContainerNetworkInfo info = new ContainerNetworkInfo();
        
        try {
            List<String> lines = Files.readAllLines(Paths.get(NETWORK_STAT_FILE));
            // 跳过前两行标题
            for (int i = 2; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] parts = line.split("\\s+");
                if (parts.length >= 17) {
                    String interfaceName = parts[0].replace(":", "");
                    // 只统计容器内网络接口
                    if (isContainerInterface(interfaceName)) {
                        info.setRxBytes(Long.parseLong(parts[1]));
                        info.setRxPackets(Long.parseLong(parts[2]));
                        info.setRxErrors(Long.parseLong(parts[3]));
                        info.setTxBytes(Long.parseLong(parts[9]));
                        info.setTxPackets(Long.parseLong(parts[10]));
                        info.setTxErrors(Long.parseLong(parts[11]));
                        break;
                    }
                }
            }
        } catch (IOException e) {
            // 忽略异常
        }
        
        return info;
    }
    
    private boolean isContainerInterface(String interfaceName) {
        // 判断是否为容器网络接口
        return interfaceName.equals("eth0") || interfaceName.startsWith("veth");
    }
}
```

### 4.3 容器生命周期事件采集

#### 4.3.1 容器启动事件
```java
public class ContainerLifecyclePlugin {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method,
                             Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        // 采集容器启动信息
        ContainerLifecycleInfo lifecycleInfo = collectLifecycleInfo();
        
        Span span = ContextManager.activeSpan();
        span.tag("container.start.time", String.valueOf(lifecycleInfo.getStartTime()));
        span.tag("container.image", lifecycleInfo.getImage());
        span.tag("container.image.id", lifecycleInfo.getImageId());
        span.tag("container.command", lifecycleInfo.getCommand());
        span.tag("container.entrypoint", lifecycleInfo.getEntrypoint());
        span.tag("container.working.dir", lifecycleInfo.getWorkingDir());
    }
    
    private ContainerLifecycleInfo collectLifecycleInfo() {
        ContainerLifecycleInfo info = new ContainerLifecycleInfo();
        
        // 从环境变量获取容器信息
        info.setImage(System.getenv("CONTAINER_IMAGE"));
        info.setImageId(System.getenv("CONTAINER_IMAGE_ID"));
        info.setCommand(System.getenv("CONTAINER_COMMAND"));
        info.setEntrypoint(System.getenv("CONTAINER_ENTRYPOINT"));
        info.setWorkingDir(System.getenv("CONTAINER_WORKING_DIR"));
        info.setStartTime(System.currentTimeMillis());
        
        return info;
    }
}
```

### 4.4 Kubernetes特定信息采集

#### 4.4.1 Pod和Service信息
```java
public class KubernetesPlugin {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method,
                             Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        // 采集Kubernetes特定信息
        KubernetesInfo k8sInfo = collectKubernetesInfo();
        
        Span span = ContextManager.activeSpan();
        span.tag("k8s.pod.name", k8sInfo.getPodName());
        span.tag("k8s.pod.namespace", k8sInfo.getPodNamespace());
        span.tag("k8s.pod.ip", k8sInfo.getPodIp());
        span.tag("k8s.node.name", k8sInfo.getNodeName());
        span.tag("k8s.service.name", k8sInfo.getServiceName());
        span.tag("k8s.deployment.name", k8sInfo.getDeploymentName());
        span.tag("k8s.replica.set", k8sInfo.getReplicaSet());
        span.tag("k8s.container.name", k8sInfo.getContainerName());
        span.tag("k8s.container.image", k8sInfo.getContainerImage());
    }
    
    private KubernetesInfo collectKubernetesInfo() {
        KubernetesInfo info = new KubernetesInfo();
        
        // 从环境变量获取K8s信息
        info.setPodName(System.getenv("POD_NAME"));
        info.setPodNamespace(System.getenv("POD_NAMESPACE"));
        info.setPodIp(System.getenv("POD_IP"));
        info.setNodeName(System.getenv("NODE_NAME"));
        info.setServiceName(System.getenv("SERVICE_NAME"));
        info.setDeploymentName(System.getenv("DEPLOYMENT_NAME"));
        info.setReplicaSet(System.getenv("REPLICA_SET"));
        info.setContainerName(System.getenv("CONTAINER_NAME"));
        info.setContainerImage(System.getenv("CONTAINER_IMAGE"));
        
        return info;
    }
}
```

### 4.5 容器健康检查信息采集

#### 4.5.1 健康状态监控
```java
public class ContainerHealthPlugin {
    private static final String HEALTH_CHECK_FILE = "/tmp/health";
    private static final String LIVENESS_PROBE_FILE = "/tmp/liveness";
    private static final String READINESS_PROBE_FILE = "/tmp/readiness";
    
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method,
                             Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        // 采集容器健康检查信息
        ContainerHealthInfo healthInfo = collectHealthInfo();
        
        Span span = ContextManager.activeSpan();
        span.tag("container.health.status", healthInfo.getHealthStatus());
        span.tag("container.liveness.status", healthInfo.getLivenessStatus());
        span.tag("container.readiness.status", healthInfo.getReadinessStatus());
        span.tag("container.last.health.check", String.valueOf(healthInfo.getLastHealthCheck()));
        span.tag("container.health.check.count", String.valueOf(healthInfo.getHealthCheckCount()));
    }
    
    private ContainerHealthInfo collectHealthInfo() {
        ContainerHealthInfo info = new ContainerHealthInfo();
        
        // 检查健康状态文件
        info.setHealthStatus(checkFileStatus(HEALTH_CHECK_FILE));
        info.setLivenessStatus(checkFileStatus(LIVENESS_PROBE_FILE));
        info.setReadinessStatus(checkFileStatus(READINESS_PROBE_FILE));
        info.setLastHealthCheck(System.currentTimeMillis());
        
        return info;
    }
    
    private String checkFileStatus(String filePath) {
        try {
            if (Files.exists(Paths.get(filePath))) {
                String content = Files.readString(Paths.get(filePath)).trim();
                return content.isEmpty() ? "healthy" : content;
            }
        } catch (IOException e) {
            // 忽略异常
        }
        return "unknown";
    }
}
```

### 4.6 容器配置信息采集

#### 4.6.1 环境变量和配置
```java
public class ContainerConfigPlugin {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method,
                             Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        // 采集容器配置信息
        ContainerConfigInfo configInfo = collectConfigInfo();
        
        Span span = ContextManager.activeSpan();
        span.tag("container.env.count", String.valueOf(configInfo.getEnvCount()));
        span.tag("container.volume.count", String.valueOf(configInfo.getVolumeCount()));
        span.tag("container.port.count", String.valueOf(configInfo.getPortCount()));
        span.tag("container.resource.limits", configInfo.getResourceLimits());
        span.tag("container.resource.requests", configInfo.getResourceRequests());
        span.tag("container.security.context", configInfo.getSecurityContext());
    }
    
    private ContainerConfigInfo collectConfigInfo() {
        ContainerConfigInfo info = new ContainerConfigInfo();
        
        // 统计环境变量数量
        Map<String, String> envVars = System.getenv();
        info.setEnvCount(envVars.size());
        
        // 从环境变量获取配置信息
        info.setResourceLimits(System.getenv("CONTAINER_RESOURCE_LIMITS"));
        info.setResourceRequests(System.getenv("CONTAINER_RESOURCE_REQUESTS"));
        info.setSecurityContext(System.getenv("CONTAINER_SECURITY_CONTEXT"));
        
        return info;
    }
}
```

---

## 五、容器化部署的最佳实践

### 5.1 容器环境配置优化

#### 5.1.1 Dockerfile配置示例
```dockerfile
# 使用官方OpenJDK镜像
FROM openjdk:11-jre-slim

# 设置工作目录
WORKDIR /app

# 复制应用JAR包
COPY target/your-application.jar app.jar

# 复制SkyWalking Agent
COPY skywalking-agent/ /skywalking-agent/

# 设置JVM参数，包含SkyWalking Agent
ENV JAVA_OPTS="-javaagent:/skywalking-agent/skywalking-agent.jar \
    -Dskywalking.agent.service_name=your-service \
    -Dskywalking.collector.backend_service=skywalking-oap:11800 \
    -Dskywalking.agent.instance_name=\${HOSTNAME}"

# 设置容器环境变量
ENV CONTAINER_IMAGE="your-application:latest"
ENV CONTAINER_COMMAND="java -jar app.jar"

# 暴露端口
EXPOSE 8080

# 启动命令
CMD java $JAVA_OPTS -jar app.jar
```

#### 5.1.2 Kubernetes Deployment配置示例
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: your-application
spec:
  replicas: 3
  selector:
    matchLabels:
      app: your-application
  template:
    metadata:
      labels:
        app: your-application
    spec:
      containers:
      - name: your-application
        image: your-application:latest
        ports:
        - containerPort: 8080
        env:
        - name: POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: POD_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
        - name: POD_IP
          valueFrom:
            fieldRef:
              fieldPath: status.podIP
        - name: NODE_NAME
          valueFrom:
            fieldRef:
              fieldPath: spec.nodeName
        - name: CONTAINER_NAME
          value: your-application
        - name: CONTAINER_IMAGE
          value: your-application:latest
        resources:
          limits:
            cpu: "1"
            memory: "1Gi"
          requests:
            cpu: "500m"
            memory: "512Mi"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
```

### 5.2 自动化部署脚本

#### 5.2.1 批量部署脚本
```bash
#!/bin/bash
# deploy-skywalking-agent.sh

# 配置参数
SKYWALKING_AGENT_VERSION="8.16.0"
SKYWALKING_OAP_HOST="skywalking-oap"
SKYWALKING_OAP_PORT="11800"

# 下载SkyWalking Agent
download_agent() {
    echo "下载SkyWalking Agent..."
    wget -O skywalking-agent.tar.gz \
        "https://archive.apache.org/dist/skywalking/${SKYWALKING_AGENT_VERSION}/apache-skywalking-java-agent-${SKYWALKING_AGENT_VERSION}.tar.gz"
    tar -xzf skywalking-agent.tar.gz
    mv skywalking-agent /opt/
    rm skywalking-agent.tar.gz
}

# 配置Agent
configure_agent() {
    echo "配置SkyWalking Agent..."
    cat > /opt/skywalking-agent/config/agent.config << EOF
agent.service_name=\${SW_AGENT_NAME:your-service}
agent.instance_name=\${SW_AGENT_INSTANCE_NAME:\${HOSTNAME}}
collector.backend_service=\${SW_AGENT_COLLECTOR_BACKEND_SERVICES:${SKYWALKING_OAP_HOST}:${SKYWALKING_OAP_PORT}}
logging.level=\${SW_LOGGING_LEVEL:INFO}
EOF
}

# 部署到容器
deploy_to_container() {
    local container_name=$1
    local service_name=$2
    
    echo "部署Agent到容器: $container_name"
    
    # 复制Agent到容器
    docker cp /opt/skywalking-agent $container_name:/skywalking-agent
    
    # 设置环境变量
    docker exec $container_name sh -c "
        echo 'export JAVA_OPTS=\"-javaagent:/skywalking-agent/skywalking-agent.jar \
            -Dskywalking.agent.service_name=$service_name \
            -Dskywalking.collector.backend_service=${SKYWALKING_OAP_HOST}:${SKYWALKING_OAP_PORT} \
            -Dskywalking.agent.instance_name=\${HOSTNAME} \$JAVA_OPTS\"' >> /etc/environment
    "
    
    # 重启容器以应用配置
    docker restart $container_name
}

# 主函数
main() {
    download_agent
    configure_agent
    
    # 批量部署到多个容器
    deploy_to_container "app1" "user-service"
    deploy_to_container "app2" "order-service"
    deploy_to_container "app3" "payment-service"
    
    echo "SkyWalking Agent部署完成！"
}

main "$@"
```

---

## 六、总结

SkyWalking Java Agent通过字节码增强、JMX接口、插件机制等多种技术手段，实现了对JVM信息、进程/线程、数据库连接池、线程池、业务ID、环境变量、Dubbo、HTTP等全方位运行信息的**无侵入自动采集**。在容器化环境中，Agent还具备**容器环境自动感知**能力，能够自动采集容器CPU、内存、网络使用情况，Kubernetes Pod信息，容器生命周期事件，健康检查状态等丰富的容器运行信息。

通过**零代码改动**的方式，只需在JVM启动参数中添加`-javaagent`，即可实现对绝大多数Java/SpringBoot业务系统的全自动、零代码改造的可观测性数据采集，包括容器化部署的应用。这极大降低了大规模系统接入的门槛和运维成本，为现代云原生应用提供了完整的可观测性解决方案。 