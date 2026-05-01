{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";
    flake-compat.url = "github:NixOS/flake-compat";
    flake-parts.url = "github:hercules-ci/flake-parts";
    make-shell = {
      url = "github:nicknovitski/make-shell";
      inputs.flake-compat.follows = "flake-compat";
    };
    nix2container.url = "github:nlewo/nix2container";
  };
  outputs = inputs @ {flake-parts, ...}:
    flake-parts.lib.mkFlake {inherit inputs;} ({...}: {
      #debug = true;
      imports = [
        inputs.make-shell.flakeModules.default
        ./nix/packages.nix
        ./nix/module.nix
      ];
      systems = [ "x86_64-linux" ];  # TODO add more when i have a way to test them.
    });
}
