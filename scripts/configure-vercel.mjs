import {readFileSync,writeFileSync} from 'node:fs';
const origin=new URL(process.argv[2]??'https://api-upstream.invalid');
if(origin.protocol!=='https:'||origin.hostname.endsWith('.invalid')||origin.username||origin.password||origin.pathname!=='/'||origin.search||origin.hash)throw new Error('Provide the actual HTTPS Render API origin, without credentials or a path.');
const file=new URL('../frontend/vercel.json',import.meta.url);const config=JSON.parse(readFileSync(file,'utf8'));
config.rewrites[0].destination=`${origin.origin}/api/:path*`;
writeFileSync(file,JSON.stringify(config,null,2)+'\n');
console.log('Updated the authoritative frontend/vercel.json. Configure trusted frontend origins on Render, then verify cookies through the deployed frontend proxy.');
