{ inputs, ... }:
{
  perSystem = { pkgs, lib, system, ... }:
  let
    my_jdk = pkgs.jdk21_headless;
    plugin = pkgs.callPackage (import ./protoc-gen-grpc-java.nix) {};

    buildArguments = rec {
      pname = "mediatoad";
      version = "0-unstable-2026-04-21";

      src = ./..;
      mvnHash = "sha256-euBnDmgAN+GbNiJuNL6ZabDeNqKGRr7ERDgtLBLRjzQ=";

      mvnJdk = my_jdk;
      mvnParameters = "-P offline";
      buildOffline = true;

      nativeBuildInputs = [pkgs.makeWrapper pkgs.protobuf plugin];

      # Test dependencies are not downloaded automatically.
      # The go-offline plugin cannot handle these so-called dynamic dependencies.
      manualMvnArtifacts = [
        # add dynamic test dependencies here
        "org.apache.maven.plugins:maven-surefire-plugin:3.5.4"
        "org.apache.maven.surefire:surefire-junit4:3.5.4"
      ];

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

    package = pkgs.maven.buildMavenPackage ({
        doCheck = false;
      }
      // buildArguments);

    nix2containerPkgs = inputs.nix2container.packages.${system};
  in {
    packages = {
      mediatoad = package;
      mediatoad-docker = nix2containerPkgs.nix2container.buildImage {
        name = "mediatoad";
        config = {
          entrypoint = [(lib.getExe package)];
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
        my_jdk
        plugin
      ];
    };
    checks = {
      test = pkgs.maven.buildMavenPackage ({
          doCheck = true;
        }
        // buildArguments);
    };
  };
}
