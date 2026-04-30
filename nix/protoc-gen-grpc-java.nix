{ stdenv, fetchurl, autoPatchelfHook }:
let
  system-data =
    if stdenv.hostPlatform.system == "x86_64-linux" then {
      suffix = "linux-x86_64.exe";
      sha256 = "sha256-lOLu6gJFQb6NDUc5b/S77cmJAzCbMqr1T6kzVdEBaDg=";
    }
    else throw ("Invalid system: " + stdenv.hostPlatform.system);
in
stdenv.mkDerivation rec {
  pname = "protoc-gen-grpc-java";
  version = "1.72.0";

  filename = "${pname}-${version}-${system-data.suffix}";
  src = fetchurl {
    url = "https://repo1.maven.org/maven2/io/grpc/${pname}/${version}/${filename}";
    inherit (system-data) sha256;
  };
  dontUnpack = true;

  nativeBuildInputs = [
    autoPatchelfHook
  ];
  buildInputs = [
    stdenv.cc.cc.lib
  ];

  installPhase = ''
    mkdir -p $out/bin
    install -m755 -D ${src} $out/bin/${pname}
  '';
}
