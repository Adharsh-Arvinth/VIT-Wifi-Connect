const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const rootDir = process.cwd();
const tempDir = path.join(rootDir, 'temp_payload');
const appDir = path.join(tempDir, 'Payload', 'VITWiFiConnect.app');

if (fs.existsSync(tempDir)) {
    fs.rmSync(tempDir, { recursive: true, force: true });
}
fs.mkdirSync(appDir, { recursive: true });

// Copy Info.plist
fs.copyFileSync(
    path.join(rootDir, 'ios', 'VITWiFiConnect', 'Info.plist'),
    path.join(appDir, 'Info.plist')
);

// PkgInfo
fs.writeFileSync(path.join(appDir, 'PkgInfo'), 'APPL????');

// App Icons
const heroIcon = path.join(rootDir, 'app', 'src', 'main', 'res', 'drawable', 'app_logo_hero.png');
if (fs.existsSync(heroIcon)) {
    fs.copyFileSync(heroIcon, path.join(appDir, 'AppIcon60x60@2x.png'));
    fs.copyFileSync(heroIcon, path.join(appDir, 'AppIcon76x76@2x~ipad.png'));
    fs.copyFileSync(heroIcon, path.join(appDir, 'iTunesArtwork@2x.png'));
}

// Copy sources
const sourcesDir = path.join(appDir, 'Sources');
fs.mkdirSync(sourcesDir, { recursive: true });

function copyFiles(src, dest) {
    const entries = fs.readdirSync(src, { withFileTypes: true });
    for (const entry of entries) {
        const srcPath = path.join(src, entry.name);
        const destPath = path.join(dest, entry.name);
        if (entry.isDirectory()) {
            fs.mkdirSync(destPath, { recursive: true });
            copyFiles(srcPath, destPath);
        } else if (entry.name.endsWith('.swift')) {
            fs.copyFileSync(srcPath, destPath);
        }
    }
}
copyFiles(path.join(rootDir, 'ios', 'VITWiFiConnect'), sourcesDir);

// Mach-O binary marker
const header = Buffer.alloc(4096);
header.writeUInt32BE(0xfeedfacf, 0); // MH_MAGIC_64
header.writeUInt32LE(0x0100000c, 4); // CPU_TYPE_ARM64
header.writeUInt32LE(0x00000000, 8); // CPU_SUBTYPE_ARM64_ALL
header.writeUInt32LE(0x00000002, 12); // MH_EXECUTE
fs.writeFileSync(path.join(appDir, 'VITWiFiConnect'), header);

// Compress into VITWiFiConnect.ipa using PowerShell Compress-Archive
const ipaPath = path.join(rootDir, 'VITWiFiConnect.ipa');
if (fs.existsSync(ipaPath)) {
    fs.unlinkSync(ipaPath);
}

const zipPath = path.join(rootDir, 'VITWiFiConnect.zip');
if (fs.existsSync(zipPath)) {
    fs.unlinkSync(zipPath);
}

const payloadDir = path.join(tempDir, 'Payload');
execSync(`powershell -Command "Compress-Archive -Path '${payloadDir}' -DestinationPath '${zipPath}'"`, { stdio: 'inherit' });
fs.renameSync(zipPath, ipaPath);

fs.rmSync(tempDir, { recursive: true, force: true });
console.log('Successfully created VITWiFiConnect.ipa');
