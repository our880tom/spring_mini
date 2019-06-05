package cn.swiftdev.example.mvc;

import cn.swiftdev.example.mvc.annotation.*;

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
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LLDispatcherServlet extends HttpServlet {

    private Properties properties;

    private List<String> classNames = new ArrayList<String>();

    private Map<String, Object> ioc = new HashMap<String, Object>();

    private List<Handle> handles = new ArrayList<Handle>();

    @Override
    public void init(ServletConfig config) throws ServletException{
        //1.加载配置文件
        doLocalConfig(config.getInitParameter("contextConfigLocation"));
        //2.扫描文件夹中的类，
        doScanner(properties.getProperty("scanPackage"));

        try {
            //3.实例化类，把类放入map中
            doInstance();
            //4.完成依赖注入
            doAutowired();
        } catch (Exception e) {
            throw new ServletException(e);
        }

        //5.初始化HandleMapping
        doHandleMapping();
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", File.separator));
        File dir = new File(url.getFile());

        for (File file : dir.listFiles()){
            if (file.isDirectory()){
                doScanner(scanPackage + "." + file.getName());
            } else {
                classNames.add(scanPackage + "." + file.getName().replace(".class", "").trim());
            }
        }
    }

    public static void main(String[] args) {
        LLDispatcherServlet ll = new LLDispatcherServlet();
        ll.doScanner("cn.dev");
    }

    private void doInstance() throws Exception {
        if (classNames.size() == 0) {
            return;
        }

        for (String className : classNames){
            Class<?> clazz = Class.forName(className);
            //如果是Controller
            if (clazz.isAnnotationPresent(LLController.class)){
                Object object = clazz.newInstance();
                ioc.put(toFirstLetterLowerCase(clazz.getSimpleName()), object);
            }

            //如果是service
            if (clazz.isAnnotationPresent(LLService.class)){
                Object object = clazz.newInstance();
                ioc.put(toFirstLetterLowerCase(clazz.getSimpleName()), object);

                //如果继承某一个接口，把value也加入到value中
                for (Class<?> c : clazz.getInterfaces()){
                    String interfaceClassName = toFirstLetterLowerCase(c.getSimpleName());
                    if (ioc.containsKey(interfaceClassName)){
                        throw new RuntimeException("the " + interfaceClassName + " is exists!");
                    }
                    ioc.put(interfaceClassName, object);
                }
            }

        }
    }

    private String toFirstLetterLowerCase(String word){
        char[] charArray =  word.toCharArray();
        charArray[0] +=  32;
        return String.valueOf(charArray);
    }

    private void doAutowired() throws Exception{
        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()){
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields){
                if (!field.isAnnotationPresent(LLAutowired.class))
                {
                    continue;
                }

                LLAutowired llAutowired = field.getAnnotation(LLAutowired.class);
                String beanName = llAutowired.value();

                if ("".equals(beanName.trim())){
                    beanName = field.getType().getName();
                    System.out.println("----class name-----" + beanName);
                }

                //私有属性可以赋值
                field.setAccessible(true);
                //为属性注入bean
                field.set(entry.getValue(), ioc.get(beanName));
            }
        }
    }

    private void doHandleMapping() {
        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(LLController.class)){
                continue;
            }

            String baseUrl = "";
            if (clazz.isAnnotationPresent(LLHandlerMapping.class)){
                LLHandlerMapping llHandlerMapping = clazz.getAnnotation(LLHandlerMapping.class);
                baseUrl = llHandlerMapping.value();
            }

            for (Method method : clazz.getMethods()){
                if (!method.isAnnotationPresent(LLHandlerMapping.class)){
                    continue;
                }

                LLHandlerMapping llHandlerMapping = method.getAnnotation(LLHandlerMapping.class);

                String regex = ("/" + baseUrl + "/" + llHandlerMapping.value())
                        .replaceAll("/+","/");

                Pattern pattern = Pattern.compile(regex);
                Handle handle = new Handle(pattern, entry.getValue(), method);
                handles.add(handle);
            }
        }
    }


    private void doLocalConfig(String contextConfigLocation) {
        System.out.println("配置文件地址：" + contextConfigLocation);
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);

        properties = new Properties();
        try {
            properties.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null){
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            //6.进行调用
            System.out.println("-------调用------");
            doDispacher(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void doDispacher(HttpServletRequest req, HttpServletResponse resp) throws Exception{
        Handle handle = getHandle(req);
        if (handle == null){
            return;
        }

        Class<?>[] paramTypes = handle.paramTypes;
        Object[] paramValues = new Object[paramTypes.length];

        Map<String, String[]> requestParamMap = req.getParameterMap();

        for (Map.Entry<String, String[]> requestParam: requestParamMap.entrySet()){
            String value = Arrays.toString(requestParam.getValue()).replaceAll("\\[|\\]","")
                    .replaceAll("\\s",",");
            if(!handle.paramIndexMapping.containsKey(requestParam.getKey())){
                continue;
            }
            int index = handle.paramIndexMapping.get(requestParam.getKey());
            //暂时只是支持String
            paramValues[index] = value;

            if (handle.paramIndexMapping.containsKey(HttpServletRequest.class.getName())){
                int respIndex = handle.paramIndexMapping.get(HttpServletRequest.class.getName());
                paramValues[respIndex] = req;
            }

            if (handle.paramIndexMapping.containsKey(HttpServletResponse.class.getName())){
                int resonseIndex = handle.paramIndexMapping.get(HttpServletResponse.class.getName());
                paramValues[resonseIndex] = resp;
            }

        }

        Object returnValue = handle.method.invoke(handle.controller, paramValues);
        if (returnValue == null ||returnValue instanceof Void){
            return;
        }

        resp.getWriter().write(returnValue.toString());
    }

    private Handle getHandle(HttpServletRequest request){
        if (handles.isEmpty()){
            return null;
        }

        String uri = request.getRequestURI();
        String  contextPath = request.getContextPath();
        String relativePath = uri.replaceAll(contextPath, "").replaceAll(File.separator+"+", File.separator);
        for (Handle handle : handles){
            Pattern pattern =  handle.pattern;
            Matcher matcher = pattern.matcher(relativePath);
            if(!matcher.matches()){
                continue;
            }
            return handle;
        }

        return null;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    public class Handle {
        private Pattern pattern;

        private Method method;

        private Object controller;

        private Class<?>[] paramTypes;

        private Map<String, Integer> paramIndexMapping;

        public Handle(Pattern pattern, Object controller, Method method) {
            this.pattern = pattern;
            this.method = method;
            this.controller = controller;
            this.paramTypes = method.getParameterTypes();

            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method){
            Annotation[][] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i ++){
                for (Annotation a : pa[i]){
                    if (a instanceof LLRequestParam){
                        String paramName = ((LLRequestParam)a).value();
                        if (!"".equals(paramName.trim())){
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }

            Class<?>[] paramTypes = method.getParameterTypes();
            for (int i = 0; i < paramTypes.length; i ++){
                Class<?> type = paramTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(), i);
                }
            }


        }

        public Pattern getPattern() {
            return pattern;

        }

        public Method getMethod() {
            return method;
        }


        public Object getController() {
            return controller;
        }


        public Class<?>[] getParamTypes() {
            return paramTypes;
        }

    }


}
