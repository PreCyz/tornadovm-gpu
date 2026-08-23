# 1. Explicit TornadoVM environment variable setup.
$env:TORNADO_HOME = "C:\Install\Java\tornadovm-5.2.0-jdk25-opencl"
$env:TORNADO_SDK  = "C:\Install\Java\tornadovm-5.2.0-jdk25-opencl"
$env:JAVA_HOME    = "C:\Install\Java\jdk-25.0.2"

# 2. Update PATH for the current session.
$env:PATH = "$env:TORNADO_HOME\bin;$env:JAVA_HOME\bin;$env:PATH"

$OPENJFX = "C:\Users\pawel\.m2\repository\org\openjfx\"

# 3. Build the classpath.
$CLASSPATH = "target\classes;$OPENJFX\javafx-controls\25.0.2\javafx-controls-25.0.2.jar;$OPENJFX\javafx-controls\25.0.2\javafx-controls-25.0.2-win.jar;$OPENJFX\javafx-graphics\25.0.2\javafx-graphics-25.0.2.jar;$OPENJFX\javafx-graphics\25.0.2\javafx-graphics-25.0.2-win.jar;$OPENJFX\javafx-base\25.0.2\javafx-base-25.0.2.jar;$OPENJFX\javafx-base\25.0.2\javafx-base-25.0.2-win.jar"

# 4. JVM flags that suppress warnings.
#$JVM_FLAGS = "-Dtornado.debug=True -Dtornado.print.kernel=True --enable-native-access=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED -Dprism.verbose=false"
$JVM_FLAGS = "--enable-native-access=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED -Dprism.verbose=false"

# 5. Launch TornadoVM.
tornado --jvm="$JVM_FLAGS" --cp $CLASSPATH pawg.Launcher @args
