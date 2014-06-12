import org.junit.Assert;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Compressor {
    public static void main(String[] args) throws IOException {
        File dir = new File("out/production/runtime");

        File classFile = new File(dir, "BitDecoding.class");

        File noDebugClassFile = new File(dir, "1.class");
        if (noDebugClassFile.delete()) {
            System.out.println("Deleted: " + noDebugClassFile);
        }
        File gzipFile = new File(dir, "BitDecoding.gzip");
        gzipFile.delete();

        stripDebugInfo(classFile, noDebugClassFile);

        System.out.println("No debug size: " + noDebugClassFile.length());

        compressAndTest(noDebugClassFile, gzipFile);
    }

    private static void stripDebugInfo(File classFile, File noDebugClassFile) throws IOException {
        ClassWriter classWriter = new ClassWriter(0);
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM4, classWriter) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                // The name "0/0" makes it virtually impossible that the class is already loaded
                // or the package is signed by anybody else
                super.visit(version, access, "0/0", signature, superName, interfaces);
            }
        };

        new ClassReader(new FileInputStream(classFile)).accept(classVisitor, ClassReader.SKIP_DEBUG);

        readAll(new ByteArrayInputStream(classWriter.toByteArray()), new FileOutputStream(noDebugClassFile));
    }

    private static void compressAndTest(File classFile, File gzipFile) throws IOException {
        compress(classFile, gzipFile);

        System.out.println("Size: " + gzipFile.length());

        // Test

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        readAll(new FileInputStream(classFile), expected);

        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        readAll(new GZIPInputStream(new FileInputStream(gzipFile)), actual);

        Assert.assertArrayEquals(expected.toByteArray(), actual.toByteArray());
    }

    private static void compress(File classFile, File gzipFile) throws IOException {
        FileOutputStream out = new FileOutputStream(gzipFile);
        GZIPOutputStream zip = new GZIPOutputStream(out);

        FileInputStream in = new FileInputStream(classFile);
        readAll(in, zip);
        zip.close();
    }

    private static void readAll(InputStream from, OutputStream to) throws IOException {
        byte[] buf = new byte[4 * 1024];
        int c;
        while ((c = from.read(buf)) != -1) {
            to.write(buf, 0, c);
        }
    }
}
