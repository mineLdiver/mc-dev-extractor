package net.mine_diver.bukric.mcdev.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class EmptyClassesTransformer {
    private static final Path
            CLASSES_DIR = Paths.get("build", "classes"),
            MCDEV_CLASSES = CLASSES_DIR.resolve("mc-dev"),
            MCDEV_TRANSFORMED_CLASSES = CLASSES_DIR.resolve("mc-dev_transformed");
    private static final Pattern EMPTY_CLASS_PATTERN = Pattern.compile(".*EmptyClass\\d+.*");

    private EmptyClassesTransformer() {
        throw new UnsupportedOperationException();
    }

    public static void main(String[] args) throws IOException {
        if (Files.exists(MCDEV_TRANSFORMED_CLASSES))
            try (Stream<Path> walk = Files.walk(MCDEV_TRANSFORMED_CLASSES)) {
                walk
                        .map(Path::toFile)
                        .sorted(Comparator.reverseOrder())
                        .forEach(File::delete);
            }
        try (Stream<Path> walk = Files.walk(MCDEV_CLASSES)) {
            walk
                    .filter(path -> Files.isRegularFile(path) && EMPTY_CLASS_PATTERN.matcher(path.toString()).matches())
                    .forEach(path -> {
                        try {
                            ClassNode classNode = new ClassNode();
                            new ClassReader(Files.readAllBytes(path)).accept(classNode, 0);
                            classNode.methods.clear();
                            ClassWriter writer = new ClassWriter(0);
                            classNode.accept(writer);
                            Path output = MCDEV_TRANSFORMED_CLASSES.resolve(MCDEV_CLASSES.relativize(path).toString());
                            Files.createDirectories(output.getParent());
                            Files.createFile(output);
                            Files.write(output, writer.toByteArray());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
