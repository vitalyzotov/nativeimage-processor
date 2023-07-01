package ru.vzotov.nativeimage.processor;


import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Set;

@SupportedAnnotationTypes("ru.vzotov.hb.nativeimage.ReflectionAccess")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ReflectionAccessProcessor extends AbstractProcessor {

    private int step = 0;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (step == 0) {
            try {
                final FileObject resource = getOrCreateResource("META-INF/native-image/reflect-config.json");

                try (final Writer writer = resource.openWriter()) {
                    writer.write("[");

                    for (TypeElement annotation : annotations) {
                        final Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
                        final Iterator<? extends Element> it = annotatedElements.iterator();
                        while (it.hasNext()) {
                            final Element el = it.next();
                            final TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(el.asType().toString());

                            writer.write("""
                                    {
                                      "name": "%1$s",
                                      "allDeclaredFields": true,
                                      "allDeclaredMethods": true,
                                      "allDeclaredConstructors": true
                                    }
                                    """.formatted(processingEnv.getElementUtils().getBinaryName(typeElement)));
                            if (it.hasNext()) {
                                writer.write(',');
                            }
                        }
                    }
                    writer.write("]");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            step++;
        }
        return true; // claim annotation
    }

    FileObject getOrCreateResource(String name) throws IOException {
        final FileObject result = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", name);
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Created resource " + result.toUri());
        return result;
    }
}
