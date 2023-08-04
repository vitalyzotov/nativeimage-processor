package ru.vzotov.nativeimage.processor;


import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static javax.tools.Diagnostic.Kind.NOTE;

@SupportedAnnotationTypes("ru.vzotov.nativeimage.annotation.ReflectionAccess")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ReflectionAccessProcessor extends AbstractProcessor {

    public static final String REFLECT_CONFIG_LOCATION = "META-INF/native-image/reflect-config.json";
    private int step = 0;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (step == 0) {
            try {
                final FileObject resource = getOrCreateResource(REFLECT_CONFIG_LOCATION);

                try (final Writer writer = resource.openWriter()) {
                    writer.write("[");
                    final Set<Name> registry = new HashSet<>();
                    for (TypeElement annotation : annotations) {
                        final Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
                        for (Element el : annotatedElements) {
                            processElement(writer, el, registry);
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

    private void processElement(Writer writer, Element element, Set<Name> registry) throws IOException {
        //System.out.println("look at: " + element);

        final TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(element.asType().toString());
        final Name key = typeElement == null ? null : processingEnv.getElementUtils().getBinaryName(typeElement);
        if (key == null || registry.contains(key)) return;

        final List<? extends Element> members = processingEnv.getElementUtils().getAllMembers(typeElement);
        for (Element e : members) {
            if (e.getKind() == ElementKind.CLASS || e.getKind() == ElementKind.RECORD || e.getKind() == ElementKind.INTERFACE) {
                processElement(writer, e, registry);
            }
        }

        if(element.getKind() == ElementKind.INTERFACE) {
            final List<? extends Element> directMembers = element.getEnclosedElements();
            for(Element e:directMembers) {
                if(e.getKind() == ElementKind.METHOD) {
                    ExecutableElement method = (ExecutableElement) e;
                    processElement(writer, processingEnv.getTypeUtils().asElement(method.getReturnType()), registry);
                }
            }
        }

        processingEnv.getMessager().printMessage(NOTE, "Allow reflection access for: " + key);
        if (!registry.isEmpty()) {
            writer.write(',');
        }
        writer.write("""
                {
                  "name": "%1$s",
                  "allDeclaredFields": true,
                  "allDeclaredMethods": true,
                  "allDeclaredConstructors": true
                }
                """.formatted(key));
        registry.add(key);
    }

    FileObject getOrCreateResource(String name) throws IOException {
        final FileObject result = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", name);
        processingEnv.getMessager().printMessage(NOTE, "Created resource " + result.toUri());
        return result;
    }
}
