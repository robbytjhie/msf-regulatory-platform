@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script, version 3.2.0
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SETLOCAL
@SET MAVEN_PROJECTBASEDIR=%~dp0
@IF NOT "%MAVEN_BASEDIR%"=="" (SET MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%)
@REM %~dp0 ends with "\"; trim it so JVM args do not get an escaped quote.
@IF "%MAVEN_PROJECTBASEDIR:~-1%"=="\" SET MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%

@REM Store paths WITHOUT embedded quotes - add quotes only at point of use
@SET WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar
@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain
@SET DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar

@REM Override download URL from properties file if present
@FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties") DO (
    @IF "%%A"=="wrapperUrl" SET DOWNLOAD_URL=%%B
)

@REM Download wrapper jar if missing
@IF NOT EXIST "%WRAPPER_JAR%" (
    @powershell -Command "&{$webclient=new-object System.Net.WebClient;[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;$webclient.DownloadFile('%DOWNLOAD_URL%','%WRAPPER_JAR%')}"
)

@REM Build java executable path - quoted so spaces in JAVA_HOME work
@IF "%JAVA_HOME%"=="" (@SET JAVA_EXE=java.exe) ELSE (@SET JAVA_EXE="%JAVA_HOME%\bin\java.exe")

@REM Launch Maven Wrapper
%JAVA_EXE% -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %MAVEN_CONFIG% %*
