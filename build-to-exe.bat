@echo off
setlocal

set APP_NAME=MyApp
set MAIN_JAR=java_runtime_controller-1.0-SNAPSHOT.jar
set MAIN_CLASS=app.MainApp
set JAVAFX_MODS=D:\Project\HCMUT\COM-MEASU-CONT\JAVA\javafx-jmods-24.0.1

echo ========== 1. Cleaning and Building Project ==========
call mvn clean package -DskipTests

if exist custom-runtime (
    echo Deleting old custom-runtime...
    rmdir /s /q custom-runtime
)

echo ========== 2. Creating Custom Java Runtime ==========
jlink ^
  --module-path "%JAVA_HOME%\jmods;%JAVAFX_MODS%" ^
  --add-modules java.base,java.desktop,javafx.controls,javafx.fxml,javafx.graphics,javafx.base,jdk.unsupported,jdk.crypto.ec ^
  --output custom-runtime

echo ========== 3. Packaging into EXE ==========
jpackage ^
  --input target ^
  --main-jar %MAIN_JAR% ^
  --name %APP_NAME% ^
  --main-class %MAIN_CLASS% ^
  --runtime-image custom-runtime ^
  --icon vtv.ico ^
  --win-menu ^
  --win-shortcut ^
  --win-dir-chooser ^
  --type exe

echo ========== Build Finished ==========
pause
endlocal
