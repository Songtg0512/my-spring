package servlet;

import annotioned.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @author songtg3
 * @createTime 2020/2/21
 * @description
 */
public class MyServlet extends HttpServlet {

    // 存储 Config 配置
    Properties config = new Properties();

    // 包下面所有的类型
    List<String> classNameList = new ArrayList<String>();

    // IOC 容器
    Map<String, Object> iocMap = new HashMap<String, Object>();

    // handleMapping 容器
    Map<String, Method> handleMap = new HashMap<String, Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            this.invokeMethod(req, resp);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 7. 进行映射，执行方法
        try {
            this.invokeMethod(req, resp);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 进行初始化
     *
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init();

        // 1.加载 web.xml 配置文件
        this.doLoadConfig(config.getInitParameter("contextConfigLocation"));

        // 2.加载 applicatiton.properties 配置文件
        this.doScanPackage(this.config.getProperty("scanPackage"));

        // 3.初始化 ioc 容器
        this.doInitIOCMap();

        // 5.进行 DI 操作，赋值
        this.autoDI();

        // 6.初始化 handleMapping 容器
        this.doUrlAndMethod();
    }

    /**
     * 进行路径匹配，执行方法
     *
     * @param req
     * @param resp
     */
    private void invokeMethod(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        // 绝对路径
        String url = req.getRequestURI();
        // 相对路径
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");
        if (!handleMap.containsKey(url)) {
            resp.getWriter().println("404 Page not found");
        } else {
            Method method = handleMap.get(url);
            //获取方法的形参列表
            Class<?>[] parameterTypes = method.getParameterTypes();
            //保存请求的url参数列表
            Map<String, String[]> parameterMap = req.getParameterMap();
            //保存赋值参数的位置
            Object[] paramValues = new Object[parameterTypes.length];
            //按根据参数位置动态赋值
            for (int i = 0; i < parameterTypes.length; i++) {
                Class parameterType = parameterTypes[i];
                if (parameterType == HttpServletRequest.class) {
                    paramValues[i] = req;
                    continue;
                } else if (parameterType == HttpServletResponse.class) {
                    paramValues[i] = resp;
                    continue;
                } else if (parameterType == String.class) {

                    //提取方法中加了注解的参数
                    Annotation[][] pa = method.getParameterAnnotations();
                    for (int j = 0; j < pa.length; j++) {
                        for (Annotation a : pa[i]) {
                            if (a instanceof GPRequestParam) {
                                String paramName = ((GPRequestParam) a).value();
                                if (!"".equals(paramName.trim())) {
                                    String value = Arrays.toString(parameterMap.get(paramName))
                                            .replaceAll("\\[|\\]", "")
                                            .replaceAll("\\s", ",");
                                    paramValues[i] = value;
                                }
                            }
                        }
                    }

                }
            }
            //投机取巧的方式
            //通过反射拿到method所在class，拿到class之后还是拿到class的名称
            //再调用toLowerFirstCase获得beanName
            String beanName = this.toLowerName(method.getDeclaringClass().getSimpleName());
            method.invoke(iocMap.get(beanName), new Object[]{req, resp, parameterMap.get("name")[0]});
        }
    }

    /**
     * 初始化 handlingMapping 容器
     */
    private void doUrlAndMethod() {
        for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (clazz.isAnnotationPresent(GPRequestMapping.class)) {
                String path = clazz.getAnnotation(GPRequestMapping.class).value().trim();
                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    String methodPath = "";
                    if (method.isAnnotationPresent(GPRequestMapping.class)) {
                        methodPath = method.getAnnotation(GPRequestMapping.class).value().trim();
                    }
                    if (!"".equals(methodPath)) {
                        String handleMethod = "/" + path + "/" + methodPath;
                        handleMap.put(handleMethod.replaceAll("/+", "/"), method);
                    }
                }
            }
        }
    }

    /**
     * 自动注入
     * 通过反射
     */
    private void autoDI() {
        if (iocMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
            // 获取到所有的属性
            Field[] declaredFields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : declaredFields) {
                if (!field.isAnnotationPresent(GPAutowired.class)) {
                    continue;
                } else {
                    String beanName = "";
                    beanName = field.getAnnotation(GPAutowired.class).value().trim();
                    if ("".equals(beanName)) {
                        // 按类型注入
                        beanName = field.getType().getName();
                    }
                    field.setAccessible(true);
                    try {
                        field.set(entry.getValue(), iocMap.get(beanName));
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }
                }
            }
        }
    }

    /**
     * 构建 Ioc 容器数据
     */
    private void doInitIOCMap() {
        for (String className : classNameList) {
            try {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(GPController.class)) {
                    Object object = clazz.newInstance();
                    String beanName = this.toLowerName(clazz.getSimpleName());
                    iocMap.put(beanName, object);
                } else if (clazz.isAnnotationPresent(GPService.class)) {
                    String beanName = "";
                    beanName = this.toLowerName(clazz.getSimpleName());
                    String annoName = clazz.getAnnotation(GPService.class).value();
                    if (!"".equals(annoName)) {
                        beanName = annoName;
                    }
                    Object object = clazz.newInstance();
                    iocMap.put(beanName, object);
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (iocMap.containsKey(i.getName())) {
                            throw new Exception("The beanName is exists!!");
                        }
                        iocMap.put(i.getName(), object);
                    }
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 把首字母小写
     *
     * @param simpleName
     * @return
     */
    private String toLowerName(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     * 加载包下面的所有类
     *
     * @param scanPackage
     */
    private void doScanPackage(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource(scanPackage
                .replaceAll("\\.", "/"));
        File classFile = new File(url.getFile());
        for (File file : classFile.listFiles()) {
            if (file.isDirectory()) {
                // 如果 file 是文件夹
                this.doScanPackage(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String className = (scanPackage + "." + file.getName()).replaceAll(".class", "");
                classNameList.add(className);
            }
        }
    }

    /**
     * 加载配置文件
     *
     * @param contextConfigLocation
     */
    private void doLoadConfig(String contextConfigLocation) {
        InputStream webInput = null;
        try {
            // 通过 classLoader 进行加载
            webInput = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
            config.load(webInput);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
