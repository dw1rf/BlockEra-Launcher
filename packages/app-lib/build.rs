use std::ffi::OsString;
use std::path::PathBuf;
use std::process::{Command, exit};
use std::{env, fs};

fn main() {
    println!("cargo::rerun-if-changed=.env");
    println!("cargo::rerun-if-changed=java/gradle");
    println!("cargo::rerun-if-changed=java/src");
    println!("cargo::rerun-if-changed=java/build.gradle.kts");
    println!("cargo::rerun-if-changed=java/settings.gradle.kts");
    println!("cargo::rerun-if-changed=java/gradle.properties");
    println!("cargo::rerun-if-changed=resources/authlib-injector-1.2.8.jar");
    println!("cargo::rerun-if-changed=../../blockera-core/build.gradle");
    println!("cargo::rerun-if-changed=../../blockera-core/settings.gradle.kts");
    println!("cargo::rerun-if-changed=../../blockera-core/gradle.properties");
    println!(
        "cargo::rerun-if-changed=../../blockera-core/gradle/wrapper/gradle-wrapper.properties"
    );
    println!("cargo::rerun-if-changed=../../blockera-core/src");
    println!(
        "cargo::rerun-if-changed=../../blockera-fabric-client/build.gradle"
    );
    println!(
        "cargo::rerun-if-changed=../../blockera-fabric-client/settings.gradle"
    );
    println!(
        "cargo::rerun-if-changed=../../blockera-fabric-client/gradle.properties"
    );
    println!(
        "cargo::rerun-if-changed=../../blockera-fabric-client/gradle/wrapper/gradle-wrapper.properties"
    );
    println!("cargo::rerun-if-changed=../../blockera-fabric-client/src/main");
    println!("cargo::rerun-if-changed=../../blockera-fabric-client/src/client");

    let authlib_injector_dir =
        fs::canonicalize("resources").expect("Missing bundled resources");
    println!(
        "cargo::rustc-env=AUTHLIB_INJECTOR_DIR={}",
        authlib_injector_dir.display()
    );

    set_env();
    build_java_jars();
    build_blockera_core();
    build_blockera_fabric_client();
}

fn set_env() {
    for (var_name, var_value) in
        dotenvy::dotenv_iter().into_iter().flatten().flatten()
    {
        if var_name == "DATABASE_URL" {
            // The sqlx database URL is a build-time detail that should not be exposed to the crate
            continue;
        }

        println!("cargo::rustc-env={var_name}={var_value}");
    }
}

fn build_java_jars() {
    let out_dir =
        dunce::canonicalize(PathBuf::from(env::var_os("OUT_DIR").unwrap()))
            .unwrap();

    println!(
        "cargo::rustc-env=JAVA_JARS_DIR={}",
        out_dir.join("java/libs").display()
    );

    let gradle_path = fs::canonicalize(
        #[cfg(target_os = "windows")]
        "java\\gradlew.bat",
        #[cfg(not(target_os = "windows"))]
        "java/gradlew",
    )
    .unwrap();

    let mut build_dir_str = OsString::from("-Dorg.gradle.project.buildDir=");
    build_dir_str.push(out_dir.join("java"));
    let exit_status = Command::new(gradle_path)
        .arg(build_dir_str)
        .arg("build")
        .arg("--no-daemon")
        .arg("--console=rich")
        .current_dir(dunce::canonicalize("java").unwrap())
        .status()
        .expect("Failed to wait on Gradle build");

    if !exit_status.success() {
        println!("cargo::error=Gradle build failed with {exit_status}");
        exit(exit_status.code().unwrap_or(1));
    }
}

fn build_blockera_core() {
    let out_dir =
        dunce::canonicalize(PathBuf::from(env::var_os("OUT_DIR").unwrap()))
            .unwrap();
    let project_dir = dunce::canonicalize("../../blockera-core")
        .expect("Missing blockera-core project");
    let build_dir = out_dir.join("blockera-core");
    let jar_path = build_dir.join("libs/blockera-core.jar");

    let gradle_path = fs::canonicalize(
        #[cfg(target_os = "windows")]
        "../../blockera-core/gradlew.bat",
        #[cfg(not(target_os = "windows"))]
        "../../blockera-core/gradlew",
    )
    .unwrap();

    let mut build_dir_arg = OsString::from("-Dorg.gradle.project.buildDir=");
    build_dir_arg.push(&build_dir);
    #[cfg(target_os = "windows")]
    let mut command = Command::new(gradle_path);
    #[cfg(not(target_os = "windows"))]
    let mut command = {
        let mut command = Command::new("sh");
        command.arg(gradle_path);
        command
    };
    if let Some(java_home) = find_gradle_java_home(17) {
        command.env("JAVA_HOME", java_home);
    }

    let exit_status = command
        .arg(build_dir_arg)
        .arg("clean")
        .arg("jar")
        .arg("--no-daemon")
        .arg("--console=rich")
        .current_dir(project_dir)
        .status()
        .expect("Failed to wait on blockera-core Gradle build");

    if !exit_status.success() || !jar_path.is_file() {
        println!("cargo::error=blockera-core build failed with {exit_status}");
        exit(exit_status.code().unwrap_or(1));
    }

    println!("cargo::rustc-env=BLOCKERA_CORE_JAR={}", jar_path.display());
}

fn find_gradle_java_home(major: u32) -> Option<PathBuf> {
    let explicit = env::var_os(format!("BLOCKERA_JAVA_{major}_HOME"))
        .or_else(|| env::var_os(format!("JAVA_HOME_{major}")))
        .or_else(|| env::var_os(format!("JAVA_HOME_{major}_X64")))
        .map(PathBuf::from)
        .filter(|path| path.join("bin").join(java_executable()).is_file());
    if explicit.is_some() {
        return explicit;
    }

    let user_profile =
        env::var_os(if cfg!(windows) { "USERPROFILE" } else { "HOME" })?;
    let jdks = PathBuf::from(user_profile).join(".gradle").join("jdks");
    let entries = fs::read_dir(jdks).ok()?;
    entries
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .find(|path| {
            path.file_name()
                .and_then(|name| name.to_str())
                .is_some_and(|name| name.contains(&format!("-{major}-")))
                && path.join("bin").join(java_executable()).is_file()
        })
}

fn java_executable() -> &'static str {
    if cfg!(windows) { "java.exe" } else { "java" }
}

fn build_blockera_fabric_client() {
    let out_dir =
        dunce::canonicalize(PathBuf::from(env::var_os("OUT_DIR").unwrap()))
            .unwrap();
    let project_dir = dunce::canonicalize("../../blockera-fabric-client")
        .expect("Missing blockera-fabric-client project");
    let build_dir = out_dir.join("blockera-fabric-client");
    let jar_path = build_dir.join("libs/blockera-core-fabric-0.1.0-dev.jar");
    let fabric_api_path =
        build_dir.join("public-runtime/fabric-api-0.141.4+1.21.11.jar");

    let gradle_path = fs::canonicalize(
        #[cfg(target_os = "windows")]
        "../../blockera-fabric-client/gradlew.bat",
        #[cfg(not(target_os = "windows"))]
        "../../blockera-fabric-client/gradlew",
    )
    .unwrap();

    let mut build_dir_arg = OsString::from("-Dorg.gradle.project.buildDir=");
    build_dir_arg.push(&build_dir);
    #[cfg(target_os = "windows")]
    let mut command = Command::new(gradle_path);
    #[cfg(not(target_os = "windows"))]
    let mut command = {
        let mut command = Command::new("sh");
        command.arg(gradle_path);
        command
    };

    let exit_status = command
        .arg(build_dir_arg)
        .arg("clean")
        .arg("remapJar")
        .arg("copyPublicRuntime")
        .arg("--no-daemon")
        .arg("--console=rich")
        .current_dir(project_dir)
        .status()
        .expect("Failed to wait on Blockera Fabric Gradle build");

    if !exit_status.success()
        || !jar_path.is_file()
        || !fabric_api_path.is_file()
    {
        println!(
            "cargo::error=blockera-fabric-client build failed with {exit_status}"
        );
        exit(exit_status.code().unwrap_or(1));
    }

    println!(
        "cargo::rustc-env=BLOCKERA_FABRIC_CLIENT_JAR={}",
        jar_path.display()
    );
    println!(
        "cargo::rustc-env=BLOCKERA_FABRIC_API_JAR={}",
        fabric_api_path.display()
    );
}
