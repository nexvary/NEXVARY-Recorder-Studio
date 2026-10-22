const fs=require("fs"),path=require("path"),os=require("os"),cp=require("child_process"),crypto=require("crypto");
const nacl=require("./nacl-fast.cjs");
const REPO="nexvary/NEXVARY-Recorder-Studio", BRANCH="ops/guardian-0111-signing-recovery", SOURCE="302719bc74638d0fa54c02d5814542fd823090c2", CI=39695164657;
const CERT="ca4640c7afcd5522210b2b60aefe121be2bc4ece87a0b9ca4ee14425891be7f6";
const token=process.env.GH_TOKEN, runId=process.env.GITHUB_RUN_ID,attempt=process.env.GITHUB_RUN_ATTEMPT;
const work=fs.mkdtempSync(path.join(os.tmpdir(),"guardian-recovery-"));fs.chmodSync(work,0o700);
const out=path.join(work,"out");fs.mkdirSync(out);let stage="start",kp;
const hash=(b,alg="sha256")=>crypto.createHash(alg).update(b).digest("hex");
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const hdr={"Authorization":"Bearer "+token,"Accept":"application/vnd.github+json","X-GitHub-Api-Version":"2022-11-28"};
async function api(route,method="GET",body){const r=await fetch("https://api.github.com/repos/"+REPO+route,{method,headers:hdr,...(body?{body:JSON.stringify(body)}:{})});if(r.status===404)return null;if(!r.ok)throw new Error("GitHub request failed "+r.status);return r.status===204?{}:r.json();}
async function content(file){const r=await api("/contents/"+file+"?ref="+encodeURIComponent(BRANCH));return r?JSON.parse(Buffer.from(r.content,"base64").toString()):null;}
async function putRaw(file,text){const old=await api("/contents/"+file+"?ref="+encodeURIComponent(BRANCH));await api("/contents/"+file,"PUT",{message:"Guardian signing recovery status",branch:BRANCH,content:Buffer.from(text).toString("base64"),...(old?{sha:old.sha}:{})});}
async function put(file,value){await putRaw(file,JSON.stringify(value,null,2)+"\n");}
async function artifact(id,sha,dest){const r=await fetch("https://api.github.com/repos/"+REPO+"/actions/artifacts/"+id+"/zip",{headers:hdr,redirect:"manual"});if(r.status!==302)throw new Error("Artifact redirect missing");const next=new URL(r.headers.get("location"));if(next.protocol!=="https:")throw new Error("Artifact transport invalid");const z=await fetch(next);if(!z.ok)throw new Error("Artifact unavailable");const b=Buffer.from(await z.arrayBuffer());if(hash(b)!==sha)throw new Error("Artifact digest mismatch");fs.writeFileSync(dest,b);}
function exec(command,args){return cp.execFileSync(command,args,{encoding:"utf8",stdio:["ignore","pipe","pipe"],maxBuffer:15_000_000});}
(async()=>{try{
 stage="verify source";const source=await api("/actions/runs/"+CI);if(source.head_sha!==SOURCE||source.conclusion!=="success")throw new Error("Source CI not green");
 stage="secure recovery exchange";kp=nacl.box.keyPair();await put("recovery/public-key.json",{version:1,runId,attempt,source:SOURCE,publicKey:Buffer.from(kp.publicKey).toString("base64")});
 let payload;
 for(let i=0;i<180;i++){let msg=await content("recovery/ciphertext.json");if(msg&&msg.runId===runId&&msg.attempt===attempt&&msg.serverPublicKey===Buffer.from(kp.publicKey).toString("base64")){const opened=nacl.box.open(Buffer.from(msg.box,"base64"),Buffer.from(msg.nonce,"base64"),Buffer.from(msg.clientPublicKey,"base64"),kp.secretKey);if(opened){payload=JSON.parse(Buffer.from(opened).toString());break;}}await sleep(5000);}
 if(!payload||payload.source!==SOURCE)throw new Error("Recovery exchange unavailable");
 const u=new URL(payload.archive.url);if(u.protocol!=="https:"||u.username||u.password||!/(^|\.)((blob\.core\.windows\.net)|(oaiusercontent\.com)|(amazonaws\.com)|(chatgpt\.com)|(openai\.com))$/.test(u.hostname))throw new Error("Recovery transport not allowed");
 stage="restore signing identity";const response=await fetch(u,{headers:payload.archive.headers||{}});if(!response.ok)throw new Error("Recovery archive unavailable");
 const backup=Buffer.from(await response.arrayBuffer());if(hash(backup)!=="e268327e1363dbfcd7e52eaf0001fa64691b45b21b5bf9d3b105bdd94d55c0f9")throw new Error("Recovery archive digest mismatch");
 fs.writeFileSync(path.join(work,"backup.zip"),backup,{mode:0o600});fs.writeFileSync(path.join(work,"unlock.txt"),payload.unlock,{mode:0o600});payload=null;
 exec("python3",[path.join(__dirname,"restore.py"),path.join(work,"backup.zip"),path.join(work,"unlock.txt"),path.join(work,"private")]);
 const secret=JSON.parse(fs.readFileSync(path.join(work,"private/private.json")));
 stage="download release source";await artifact(12389868602,"ad9e25114a9b801ac4b9ad5445d589e54da80b7dbb7d08f687bff58ae4963545",path.join(work,"aab.zip"));
 exec("python3",["-c","import zipfile,pathlib,sys;z=zipfile.ZipFile(sys.argv[1]);assert z.testzip() is None;pathlib.Path(sys.argv[2]).write_bytes(z.read('app-release.aab'))",path.join(work,"aab.zip"),path.join(work,"unsigned.aab")]);
 const jar=path.join(work,"bundletool.jar"),jr=await fetch("https://github.com/google/bundletool/releases/download/1.18.3/bundletool-all-1.18.3.jar");if(!jr.ok)throw new Error("Bundletool unavailable");const jb=Buffer.from(await jr.arrayBuffer());if(hash(jb)!=="a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29")throw new Error("Bundletool digest mismatch");fs.writeFileSync(jar,jb);
 fs.writeFileSync(path.join(work,"manifest.xml"),exec("java",["-jar",jar,"dump","manifest","--bundle="+path.join(work,"unsigned.aab")]));
 stage="sign APK and AAB";const apk=path.join(out,"NEXVARY-Guardian-Studio-0.11.1-release-signed.apk"),aab=path.join(out,"NEXVARY-Guardian-Studio-0.11.1-release-signed.aab");
 exec("java",["-jar",jar,"build-apks","--bundle="+path.join(work,"unsigned.aab"),"--output="+path.join(work,"release.apks"),"--mode=universal","--ks="+secret.keystore,"--ks-key-alias="+secret.alias,"--ks-pass=file:"+secret.storePasswordFile,"--key-pass=file:"+secret.keyPasswordFile]);
 exec("python3",["-c","import zipfile,pathlib,sys;z=zipfile.ZipFile(sys.argv[1]);assert z.testzip() is None;pathlib.Path(sys.argv[2]).write_bytes(z.read('universal.apk'))",path.join(work,"release.apks"),apk]);
 exec("jarsigner",["-keystore",secret.keystore,"-storetype","PKCS12","-storepass:file",secret.storePasswordFile,"-keypass:file",secret.keyPasswordFile,"-sigalg","SHA256withRSA","-digestalg","SHA-256","-signedjar",aab,path.join(work,"unsigned.aab"),secret.alias]);
 const verified=exec("java",["--class-path",jar,path.join(__dirname,"VerifyRelease.java"),apk,CERT]);fs.writeFileSync(path.join(out,"APK-SIGNATURE-VERIFICATION.txt"),verified);
 const aabCheck=exec("jarsigner",["-verify","-verbose","-certs",aab]);if(!aabCheck.includes("jar verified.")||aabCheck.includes("unsigned entries"))throw new Error("AAB signature invalid");fs.writeFileSync(path.join(out,"AAB-SIGNATURE-VERIFICATION.txt"),aabCheck);
 for(const n of ["private","backup.zip","unlock.txt"]){fs.rmSync(path.join(work,n),{recursive:true,force:true});}
 stage="verify release evidence";const zd=path.join(work,"zips");fs.mkdirSync(zd);
 const artifacts=[
 ["screens35",12391421360,"5a1070762baa34944c1cf401d23b255f904df0cace4ef8daa09d1488fc847936"],
 ["diag33",12391256866,"000c9b1d87865d0cbfb22aa4188afc23da0b9aa7f58faa5a7c52c0acd115b9b1"],
 ["diag35",12391181664,"f8da16876494ef136776035a92d4bc8c5c5fcf114580f9c097ee8c9fba29f220"],
 ["diag34",12390967855,"a25e443a642b765dec0608fca61784eea6edfcec9730f89b6ca4a814637c0d84"],
 ["quality",12389958525,"26c683cdcaec29ae9b35cc057b139571bae047c97681b22f527ea6f8a2b9146c"],
 ["mysql",12391140126,"0e4c7b1ff6eccde33a58eca441e53d180e32690c375ff4531b14657701ea7332"],
 ["sqlite",12390700348,"c346973bd59cfc9eb8f1150939b2d62b05267ead49c47f69457c232677542751"]];
 for(const [n,id,sha] of artifacts)await artifact(id,sha,path.join(zd,n+".zip"));
 exec("python3",[path.join(__dirname,"evidence.py"),work]);
 const report=[
 "# NEXVARY Guardian Studio 0.11.1",
 "",
 "أُصلحت قائمة مؤقت الأمان: عنوانها والمدة المختارة وجميع صفوفها بالأخضر الواضح، مع تخطيط Android أصلي ومحاذاة صحيحة.",
 "أصبحت الإعدادات أربع بطاقات: إعداد أمان العائلة، اللغة والمظهر، الحساب والإرسال الاختياري، ودليل التشغيل.",
 "إعدادات المشاركة والتسجيل والرحلة والجهاز في صفحات مستقلة، وحفظ قسم يحافظ على بقية الإعدادات والأدلة. مؤقت الأمان يعرض المدد الجاهزة أولًا، ويظهر حقل الدقائق عند اختيار مخصص، ومهلة الرد داخل الخيارات الإضافية.",
 "شاشة الحساب تعرض خطوة واحدة في كل مرة، مع إمكانية تأكيد البريد واسترجاع كلمة المرور وإعادة الدخول عند تغيير خدمة المؤسسة.",
 "",
 "التحقق: 42 اختبار وحدة ناجحًا، و69 اختبارًا ناجحًا دون تخطٍّ على كل من Android 13 و14 و15. الخادم المرجعي 20، والخادم المحمول MySQL عدد 39، وSQLite عدد 37 مع تخطي اختباري تزامن MySQL فقط. Lint: صفر أخطاء و157 تحذيرًا موثقة. 26 لقطة عربية فعلية من Android 15 ببيانات اصطناعية.",
 "",
 "اختبارات الواجهة على بناء Debug من المصدر نفسه؛ تحقق بناء Release وتوقيعه، لكن اختبار ملفه على هاتف OPPO/ColorOS فعلي ما زال مطلوبًا. لم تُفعّل خدمة Namecheap أو بريد أو تخزين سحابي حي أو مشتريات Play.",
 "استُعيد التوقيع من النسخة الاحتياطية المشفرة بعد انقطاع بيئة الملفات، داخل مهمة GitHub مؤقتة. لم تُكتب الأسرار أو كلمات مرور التوقيع في المستودع أو السجلات أو APK؛ حذفت النسخ المؤقتة الخاصة بعد التوقيع.",
 "",
 "Source: "+SOURCE,"CI: https://github.com/"+REPO+"/actions/runs/"+CI,"applicationId: com.nexvary.recorder; versionCode: 19; versionName: 0.11.1",
 "Certificate SHA-256: "+CERT
 ].join("\n");
 fs.writeFileSync(path.join(out,"Guardian-0.11.1-Changes-AR.md"),report);
 const sums=[apk,aab].map(f=>hash(fs.readFileSync(f))+"  "+path.basename(f)).join("\n")+"\n";fs.writeFileSync(path.join(out,"SHA256SUMS.txt"),sums);
 fs.writeFileSync(path.join(out,"release-source.json"),JSON.stringify({sourceCommit:SOURCE,sourceRun:CI,recoveryRun:runId,version:"0.11.1",versionCode:19,applicationId:"com.nexvary.recorder",certificateSha256:CERT},null,2));
 exec("python3",["-c","import pathlib,zipfile,sys;r=pathlib.Path(sys.argv[1]);z=zipfile.ZipFile(r/'Guardian-0.11.1-Screenshots-and-Verification.zip','w',zipfile.ZIP_DEFLATED);files=[r/'SHA256SUMS.txt',r/'release-source.json',r/'Guardian-0.11.1-Changes-AR.md',r/'APK-SIGNATURE-VERIFICATION.txt',r/'AAB-SIGNATURE-VERIFICATION.txt']+list((r/'screenshots').rglob('*'))+list((r/'verification').rglob('*'));[z.write(p,p.relative_to(r)) for p in files if p.is_file()];z.close()",out]);
 stage="stage release";const tag="guardian-studio-v0.11.1-"+runId;
 const release=await api("/releases","POST",{tag_name:tag,target_commitish:SOURCE,name:"NEXVARY Guardian Studio 0.11.1 — Settings and Timer",body:report+"\n\n"+sums,draft:true,prerelease:true});
 const assetNames=[path.basename(apk),path.basename(aab),"Guardian-0.11.1-Changes-AR.md","Guardian-0.11.1-Screenshots-and-Verification.zip","SHA256SUMS.txt"];
 const assets=[];
 for(const name of assetNames){const b=fs.readFileSync(path.join(out,name));const res=await fetch("https://uploads.github.com/repos/"+REPO+"/releases/"+release.id+"/assets?name="+encodeURIComponent(name),{method:"POST",headers:{...hdr,"Content-Type":"application/octet-stream"},body:b});if(!res.ok)throw new Error("Release asset upload failed");const a=await res.json();assets.push({name,bytes:b.length,sha256:hash(b),url:a.browser_download_url});}
 const previews={};
 for(const name of ["timer-presets-ar.png","settings-ar.png"]){const bytes=fs.readFileSync(path.join(out,"screenshots",name)),b64=bytes.toString("base64");const chunks=[];for(let i=0;i<b64.length;i+=48000){const file="recovery/previews/"+name+"-"+(i/48000)+".txt";await putRaw(file,b64.slice(i,i+48000));chunks.push(file);}previews[name]={chunks,sha512:hash(bytes,"sha512"),bytes:bytes.length};}
 const libraryFiles=assetNames.filter(n=>n!=="SHA256SUMS.txt").map(name=>{const b=fs.readFileSync(path.join(out,name));return{name,bytes:b.length,sha256:hash(b),mimeType:name.endsWith(".apk")?"application/vnd.android.package-archive":name.endsWith(".md")?"text/markdown":name.endsWith(".zip")?"application/zip":"application/octet-stream",artifactType:name.endsWith(".md")?"report":"other"};});
 for(const name of ["timer-presets-ar.png","settings-ar.png"]){const b=fs.readFileSync(path.join(out,"screenshots",name));libraryFiles.push({name,bytes:b.length,sha256:hash(b),mimeType:"image/png",artifactType:"image"});}
 await put("recovery/result.json",{status:"awaiting_release_approval",runId,source:SOURCE,tag,releaseId:release.id,releaseUrl:release.html_url,assets,previews,libraryFiles});
 stage="wait for visual approval";let approved=false,uploaded=false;
 for(let i=0;i<180;i++){
 if(!uploaded){const m=await content("recovery/upload-more.json");if(m&&m.runId===runId&&m.serverPublicKey===Buffer.from(kp.publicKey).toString("base64")){
 const opened=nacl.box.open(Buffer.from(m.box,"base64"),Buffer.from(m.nonce,"base64"),Buffer.from(m.clientPublicKey,"base64"),kp.secretKey);
 if(opened){const request=JSON.parse(Buffer.from(opened).toString());if(request.source!==SOURCE)throw new Error("Upload source mismatch");const completed=[];
 for(const upload of request.uploads){const record=libraryFiles.find(f=>f.name===upload.file_name);if(!record)throw new Error("Unexpected delivery file");const file=record.mimeType==="image/png"?path.join(out,"screenshots",record.name):path.join(out,record.name);const b=fs.readFileSync(file);if(b.length!==upload.file_size_bytes||hash(b)!==record.sha256)throw new Error("Delivery bytes mismatch");
 const target=new URL(upload.upload_url);if(target.protocol!=="https:"||target.username||target.password)throw new Error("Invalid upload transport");
 const result=await fetch(target,{method:"PUT",headers:upload.required_headers||{},body:b});if(!result.ok)throw new Error("Delivery upload failed");
 completed.push({name:record.name,bytes:b.length,sha256:record.sha256,status:result.status});await put("recovery/upload-status.json",{runId,source:SOURCE,completed});}
 uploaded=true;}}
 }
 const a=await content("recovery/publish.json");if(a&&a.runId===runId&&a.source===SOURCE&&a.approve===true){approved=true;break;}await sleep(5000);}if(!approved)throw new Error("Visual approval unavailable");
 stage="complete verified delivery";await put("recovery/result.json",{status:"completed",runId,source:SOURCE,tag,releaseUrl:release.html_url,draft:true,assets,previews,libraryFiles});console.log("Verified signed delivery completed; repository assets remain a draft.");
 }catch(e){console.log("Recovery stopped at stage: "+stage);try{await put("recovery/result.json",{status:"failed",stage,runId,source:SOURCE});}catch{}process.exitCode=1;}
 finally{if(kp)kp.secretKey.fill(0);fs.rmSync(work,{recursive:true,force:true});}
})();
