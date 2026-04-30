{ withSystem, moduleWithSystem, inputs, ... }:
{
  flake.nixosModules.default = moduleWithSystem (perSystem@{ config, self', ... }:
  { pkgs, lib, config, ... }:
  with lib;
  let
    cfg = config.services.mediatoad;
  in
  {
    options.services.mediatoad = {
      enable = mkEnableOption "enable mediatoad";

      interface = mkOption {
        description = "address to bind to";
        type = types.str;
        default = "0.0.0.0";
      };

      groups = mkOption {
        description = "list of groups to attach via SupplementaryGroups";
        type = types.listOf types.str;
        default = [];
      };

      mediaPaths = mkOption {
        description = "list of media directories";
        type = types.listOf types.str;
      };

      userfile = mkOption {
        description = "list of lines of userfile";
        type = types.listOf types.str;
        default = [];
      };

      extraArgs = mkOption {
        type = with types; listOf str;
        default = [];
      };
    };

    config = mkIf cfg.enable {
      systemd.services.mediatoad = {
        after = [ "network-online.target" ];
        requires = [ "network-online.target" ];
        wantedBy = [ "default.target" ];
        path = [ pkgs.ffmpeg-headless ];
        preStart = ''
          mkdir -p "''${STATE_DIRECTORY}/sessions"
          mkdir -p "''${STATE_DIRECTORY}/thumbs"
        '';
        serviceConfig = {
          ExecStart = lib.escapeShellArgs([
              "${getExe perSystem.self'.packages.mediatoad}"
              "--interface"  cfg.interface
              "--idfile"     "\${STATE_DIRECTORY}/systemid"
              "--tree"       (pkgs.writeText "tree.txt" (lib.concatStringsSep "\n" cfg.mediaPaths))
              "--userfile"   (pkgs.writeText "userfile" (lib.concatStringsSep "\n" cfg.userfile))
              "--sessiondir" "\${STATE_DIRECTORY}/sessions"
              "--rpcauth"    "\${STATE_DIRECTORY}/rpcauthfile"
              "--db"         "\${STATE_DIRECTORY}/db"
              "--thumbs"     "\${STATE_DIRECTORY}/thumbs"
              "--http-path-prefix" "mediatoad"
              "--trust-forwarded-header"
          ] ++ cfg.extraArgs);

          DynamicUser = true;
          SupplementaryGroups = cfg.groups;
          StateDirectory = "mediatoad";

          Restart = "always";
          RestartSec = "60";
          KillSignal = "SIGINT";
          TimeoutStopSec = "30";

          # some might be mostly irrevelent when running as dynamic user.
          # but setting anyway as a precaution, or if switched to a specific user.
          AmbientCapabilities = "";
          CapabilityBoundingSet = "";
          DevicePolicy = "closed";
          LockPersonality = true;
          MemoryDenyWriteExecute = false; # does not work with JVM
          NoNewPrivileges = true;
          PrivateDevices = true;
          PrivateTmp = true;
          ProcSubset = "pid";
          ProtectControlGroups = true;
          ProtectHome = true;
          ProtectKernelModules = true;
          ProtectKernelTunables = true;
          ProtectProc = "noaccess";
          ProtectSystem = "strict";
          RestrictAddressFamilies = "AF_INET AF_INET6";
          RestrictNamespaces = true;
          RestrictRealtime = true;
          RestrictSUIDSGID = true;
          SystemCallArchitectures = "native";
        };
      };
    };

    # TODO setup nginx (if enabled by an option).

  }
  );
}
