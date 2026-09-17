{ inputs, ... }:
{
  perSystem = { pkgs, lib, system, ... }:
  let
    my_jdk = pkgs.jdk21_headless;
    plugin = pkgs.callPackage (import ./protoc-gen-grpc-java.nix) {};

    package = pkgs.maven.buildMavenPackage rec {
      pname = "mediatoad";
      version = "1";

      src = ./..;
      mvnHash = "sha256-ZcOAVxcLn25S+mn6rK7ApMd4bkfLwU5NAGlP9DoSLBQ=";

      mvnJdk = my_jdk;
      mvnParameters = "-P offline";
      buildOffline = true;
      doCheck = false;

      nativeBuildInputs = [ pkgs.makeWrapper pkgs.protobuf plugin ];

      installPhase = ''
        mkdir -p $out/bin $out/share/${pname}
        install -Tm644 \
          target/${pname}-1-SNAPSHOT-jar-with-dependencies.jar \
          $out/share/${pname}/${pname}.jar

        makeWrapper ${lib.getExe my_jdk} $out/bin/${pname} \
          --add-flags "\
            -XX:+PerfDisableSharedMem \
            -XX:-UsePerfData \
            -Xmx1024m \
            -Djava.net.preferIPv4Stack=true \
            -jar $out/share/${pname}/${pname}.jar \
          "
      '';

      meta = with lib; {
        description = "mediatoad media server";
        homepage = "https://github.com/haku/mediatoad";
        license = licenses.asl20;
        mainProgram = pname;
      };
    };

    nix2containerPkgs = inputs.nix2container.packages.${system};
  in {
    packages = {
      mediatoad = package;
      mediatoad-docker = nix2containerPkgs.nix2container.buildImage {
        name = "mediatoad";
        config = {
          entrypoint = [ (lib.getExe package) ];
          exposedPorts = {
            "8192/tcp" = {};
          };
        };
        layers = [
          (nix2containerPkgs.nix2container.buildLayer {deps = [my_jdk plugin];})
        ];
      };
    };
    make-shells.default = {
      packages = [
        pkgs.maven
        my_jdk
        plugin
      ];
    };
  };
}
