package com.labreport.writer.engine;

import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * CodeAnalyzer - Java字节码分析器。
 * <p>
 * 使用ASM库读取编译后的.class文件，提取类结构信息
 * （类名、字段、方法、继承关系），然后生成 PlantUML 类图 DSL。
 * </p>
 *
 * <pre>
 * 使用方式:
 *   CodeAnalyzer analyzer = new CodeAnalyzer();
 *   String plantUml = analyzer.analyzeDirectory(Path.of("target/classes"));
 *   // 然后将 plantUml 传给 UmlGenerator.generateToFile()
 * </pre>
 */
public class CodeAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CodeAnalyzer.class);

    /** 要包含在UML中的访问修饰符 */
    private static final int ACCESS_MASK = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED;

    /**
     * 类信息记录。
     */
    public static class ClassInfo {
        public String className;         // 完全限定名 (com.example.MyClass)
        public String simpleName;        // 短名 (MyClass)
        public String superName;         // 父类名
        public List<String> interfaces = new ArrayList<>(); // 接口列表
        public List<FieldInfo> fields = new ArrayList<>();   // 字段
        public List<MethodInfo> methods = new ArrayList<>(); // 方法
        public int access;               // 访问修饰符
        public boolean isInterface;
        public boolean isAbstract;
        public boolean isEnum;
        public boolean isRecord;

        /** 获取UML中的类类型标记 */
        public String getUmlType() {
            if (isInterface) return "interface";
            if (isEnum) return "enum";
            if (isAbstract) return "abstract class";
            return "class";
        }

        /** 获取简化的类名（用于UML显示） */
        public String getDisplayName() {
            int lastDot = simpleName.lastIndexOf('.');
            return lastDot >= 0 ? simpleName.substring(lastDot + 1) : simpleName;
        }
    }

    public record FieldInfo(String name, String type, String access) {}
    public record MethodInfo(String name, String returnType, List<String> paramTypes, String access) {}

    // ---- 分析入口 ----

    /**
     * 分析单个.class文件。
     */
    public ClassInfo analyzeClassFile(Path classFile) throws IOException {
        try (InputStream is = Files.newInputStream(classFile)) {
            ClassReader reader = new ClassReader(is);
            ClassInfoCollector collector = new ClassInfoCollector();
            reader.accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            return collector.getClassInfo();
        }
    }

    /**
     * 分析目录下所有.class文件。
     */
    public List<ClassInfo> analyzeDirectory(Path directory) throws IOException {
        List<ClassInfo> result = new ArrayList<>();
        try (var stream = Files.walk(directory)) {
            stream.filter(p -> p.toString().endsWith(".class"))
                  .forEach(p -> {
                      try {
                          result.add(analyzeClassFile(p));
                      } catch (IOException e) {
                          log.warn("无法分析: {}", p, e);
                      }
                  });
        }
        log.info("分析了 {} 个类文件", result.size());
        return result;
    }

    /**
     * 分析JAR文件中的所有.class文件。
     */
    public List<ClassInfo> analyzeJar(Path jarFile) throws IOException {
        List<ClassInfo> result = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFile.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        ClassReader reader = new ClassReader(is);
                        ClassInfoCollector collector = new ClassInfoCollector();
                        reader.accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                        result.add(collector.getClassInfo());
                    } catch (Exception e) {
                        log.debug("跳过: {}", entry.getName());
                    }
                }
            }
        }
        log.info("从JAR分析了 {} 个类文件", result.size());
        return result;
    }

    // ---- PlantUML DSL 生成 ----

    /**
     * 从类信息列表生成PlantUML类图DSL。
     */
    public String generatePlantUml(List<ClassInfo> classes) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("skinparam backgroundColor #FEFEFE\n");
        sb.append("skinparam classBorderColor #2196F3\n");
        sb.append("skinparam classFontColor #333333\n");
        sb.append("skinparam classFontName Times New Roman\n");
        sb.append("skinparam classAttributeFontSize 11\n");
        sb.append("\n");

        // 生成类定义
        Set<String> declaredClasses = new HashSet<>();
        for (ClassInfo ci : classes) {
            String name = sanitizeName(ci.getDisplayName());
            declaredClasses.add(name);

            sb.append(ci.getUmlType()).append(" ").append(name).append(" {\n");

            // 字段
            for (FieldInfo f : ci.fields) {
                String accessSymbol = accessSymbol(f.access());
                String type = sanitizeType(f.type());
                sb.append("  ").append(accessSymbol).append(f.name())
                  .append(": ").append(type).append("\n");
            }

            // 方法
            for (MethodInfo m : ci.methods) {
                String accessSymbol = accessSymbol(m.access());
                String ret = sanitizeType(m.returnType());
                String params = String.join(", ", m.paramTypes().stream()
                    .map(CodeAnalyzer::sanitizeType).toList());
                sb.append("  ").append(accessSymbol).append(m.name())
                  .append("(").append(params).append("): ").append(ret).append("\n");
            }

            sb.append("}\n\n");
        }

        // 生成关系
        for (ClassInfo ci : classes) {
            String child = sanitizeName(ci.getDisplayName());

            // 继承关系
            if (ci.superName != null && !ci.superName.equals("java/lang/Object")) {
                String parent = sanitizeName(shortName(ci.superName));
                if (declaredClasses.contains(parent)) {
                    sb.append(parent).append(" <|-- ").append(child).append("\n");
                }
            }

            // 接口实现
            for (String iface : ci.interfaces) {
                String ifaceName = sanitizeName(shortName(iface));
                if (declaredClasses.contains(ifaceName)) {
                    sb.append(ifaceName).append(" <|.. ").append(child).append("\n");
                }
            }
        }

        sb.append("@enduml\n");
        return sb.toString();
    }

    // ---- ASM ClassVisitor ----

    private static class ClassInfoCollector extends ClassVisitor {
        private ClassInfo info = new ClassInfo();

        ClassInfoCollector() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name,
                String signature, String superName, String[] interfaces) {
            info.className = name;
            info.simpleName = name.replace('/', '.');
            info.superName = superName;
            info.access = access;
            info.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            info.isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
            info.isEnum = (access & Opcodes.ACC_ENUM) != 0;
            // Java 14+ record (ACC_RECORD = 0x10)
            info.isRecord = (access & 0x10) != 0;
            if (interfaces != null) {
                info.interfaces.addAll(Arrays.asList(interfaces));
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name,
                String descriptor, String signature, Object value) {
            if ((access & ACCESS_MASK) != 0) {
                String type = Type.getType(descriptor).getClassName();
                String accessStr = accessToString(access);
                info.fields.add(new FieldInfo(name, type, accessStr));
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name,
                String descriptor, String signature, String[] exceptions) {
            if ((access & ACCESS_MASK) != 0 && !name.startsWith("<")) {
                Type returnType = Type.getReturnType(descriptor);
                Type[] paramTypes = Type.getArgumentTypes(descriptor);
                String accessStr = accessToString(access);
                List<String> params = Arrays.stream(paramTypes)
                    .map(Type::getClassName)
                    .toList();
                info.methods.add(new MethodInfo(name,
                    returnType.getClassName(), params, accessStr));
            }
            return null;
        }

        ClassInfo getClassInfo() { return info; }

        private static String accessToString(int access) {
            if ((access & Opcodes.ACC_PUBLIC) != 0) return "+";
            if ((access & Opcodes.ACC_PROTECTED) != 0) return "#";
            return "-";
        }
    }

    // ---- 工具方法 ----

    private static String accessSymbol(String access) {
        return switch (access) {
            case "+" -> "+";
            case "#" -> "#";
            default -> "-";
        };
    }

    private static String sanitizeName(String name) {
        return name.replace('.', '_').replace('$', '_');
    }

    private static String sanitizeType(String type) {
        return type.replace("java.lang.", "")
                   .replace("java.util.", "")
                   .replace("java.io.", "");
    }

    private static String shortName(String internalName) {
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
    }

    // ---- main() 用于独立测试 ----

    public static void main(String[] args) throws Exception {
        CodeAnalyzer analyzer = new CodeAnalyzer();

        // 分析自己（target/classes）
        Path classesDir = Path.of("target", "classes");
        if (Files.exists(classesDir)) {
            List<ClassInfo> classes = analyzer.analyzeDirectory(classesDir);
            classes.forEach(c -> System.out.println("  " + c.getUmlType() + " " + c.getDisplayName()));

            String plantUml = analyzer.generatePlantUml(classes);
            System.out.println("\n--- 生成的 PlantUML ---\n");
            System.out.println(plantUml);
        } else {
            System.out.println("请先编译项目: mvn compile");
        }
    }
}
