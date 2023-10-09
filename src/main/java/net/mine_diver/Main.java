package net.mine_diver;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");
//
//        System.out.println(MappingReader.detectFormat(Paths.get("mcdev.srg")));
//
//        Map<Map.Entry<String, String>, String> fieldDescriptors = new HashMap<>();
//
//        ZipFile zipFile = new ZipFile("build/libs/mc-dev.jar");
//        Enumeration<? extends ZipEntry> entries = zipFile.entries();
//
//        while (entries.hasMoreElements()) {
//            ZipEntry zipEntry = entries.nextElement();
//            if (zipEntry.isDirectory() || !zipEntry.getName().endsWith(".class")) continue;
//            ClassNode classNode = new ClassNode();
//            try (InputStream stream = zipFile.getInputStream(zipEntry)) {
//                new ClassReader(stream).accept(classNode, 0);
//            }
//            classNode.fields.forEach(fieldNode -> fieldDescriptors.put(new AbstractMap.SimpleEntry<>(classNode.name, fieldNode.name), fieldNode.desc));
//        }
//        zipFile.close();
//
//        FileWriter writer = new FileWriter(Paths.get("mcdev2obf.tiny").toFile());
//        MappingReader.read(
//                Paths.get("mcdev.srg"),
//                new MappingSourceNsSwitch(
//                        new ForwardingMappingVisitor(MappingWriter.create(writer, MappingFormat.TINY_2)) {
//                            private String classSrcName;
//
//                            @Override
//                            public boolean visitClass(String srcName) throws IOException {
//                                return super.visitClass(classSrcName = srcName);
//                            }
//
//                            @Override
//                            public boolean visitField(String srcName, String srcDesc) throws IOException {
//                                return super.visitField(srcName, srcDesc == null ? fieldDescriptors.get(new AbstractMap.SimpleEntry<>(classSrcName, srcName)) : srcDesc);
//                            }
//                        },
//                        "target"
//                )
//        );
//        writer.flush();
//        writer.close();
//
//        TinyRemapper remapper = TinyRemapper
//                .newRemapper()
//                .withMappings(TinyUtils.createTinyMappingProvider(
//                        Paths.get("biny-merged.tiny"),
//                        "server",
//                        "named"
//                ))
//                .build();
//        try (OutputConsumerPath output = new OutputConsumerPath.Builder(Paths.get("craftbukkit-biny.jar")).build()) {
//            output.addNonClassFiles(Paths.get("craftbukkit-obf.jar"), NonClassCopyMode.UNCHANGED, remapper);
//            remapper.readInputs(Paths.get("craftbukkit-obf.jar"));
//            remapper.apply(output);
//        } finally {
//            remapper.finish();
//        }
    }
}