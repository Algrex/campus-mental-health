import {createHash} from 'node:crypto';
import {access, mkdir, readFile, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const properties = Object.fromEntries(
  (
    await readFile(
      path.join(repo, 'backend/.mvn/wrapper/maven-wrapper.properties'),
      'utf8',
    )
  )
    .split(/\r?\n/)
    .filter((line) => line.includes('=') && !line.startsWith('#'))
    .map((line) => {
      const separator = line.indexOf('=');
      return [
        line.slice(0, separator).trim(),
        line.slice(separator + 1).trim(),
      ];
    }),
);
const url = properties.distributionUrl;
if (!url?.endsWith('-bin.zip') || !properties.distributionSha256Sum) {
  throw new Error(
    'Wrapper must specify a ZIP distribution and SHA256 checksum.',
  );
}
const distribution = path.posix
  .basename(new URL(url).pathname)
  .replace(/-bin\.zip$/, '');
const urlHash = createHash('sha256').update(url).digest('hex');
const target = path.join(
  repo,
  '.tools/maven-user/wrapper/dists',
  distribution,
  urlHash,
);

async function downloadDistribution() {
  let lastError;
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`Maven download failed: HTTP ${response.status}`);
      }
      return Buffer.from(await response.arrayBuffer());
    } catch (error) {
      lastError = error;
      if (attempt < 2) {
        await new Promise((resolve) =>
          setTimeout(resolve, 500 * (attempt + 1)),
        );
      }
    }
  }
  throw lastError;
}

try {
  await access(path.join(target, 'bin/mvn.cmd'));
  console.log(JSON.stringify({cached: true, target}));
} catch {
  const bytes = await downloadDistribution();
  const checksum = createHash('sha256').update(bytes).digest('hex');
  if (checksum !== properties.distributionSha256Sum.toLowerCase()) {
    throw new Error('Maven distribution SHA256 verification failed.');
  }
  await mkdir(path.join(repo, '.tools'), {recursive: true});
  const archive = path.join(repo, '.tools/maven-distribution.zip');
  await writeFile(archive, bytes);
  console.log(JSON.stringify({cached: false, target, archive, distribution}));
}
