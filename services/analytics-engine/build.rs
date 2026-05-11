fn main() -> Result<(), Box<dyn std::error::Error>> {
    tonic_build::configure()
        .build_server(true)
        .build_client(false)
        .out_dir("src/_gen")
        .compile_protos(
            &["../../proto/v1/analytics_engine.proto"],
            &["../../proto"],
        )?;
    Ok(())
}
