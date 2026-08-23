#!/usr/bin/env python3
import json, struct, sys, zipfile
from pathlib import Path
NO=0xffffffff

def u8len(buf,pos):
    a=buf[pos];pos+=1
    if a&0x80:
        b=buf[pos];pos+=1
        return ((a&0x7f)<<8)|b,pos
    return a,pos

def parse_pool(chunk):
    typ,hs,size=struct.unpack_from('<HHI',chunk,0)
    if typ!=1: raise ValueError('string pool absent')
    count,styles,flags,start,style_start=struct.unpack_from('<IIIII',chunk,8)
    offs=struct.unpack_from('<%dI'%count,chunk,hs) if count else []
    out=[]
    utf8=bool(flags&0x100)
    for off in offs:
        p=start+off
        if utf8:
            _,p=u8len(chunk,p); n,p=u8len(chunk,p); out.append(chunk[p:p+n].decode('utf-8','replace'))
        else:
            n=struct.unpack_from('<H',chunk,p)[0];p+=2
            if n&0x8000:
                n=((n&0x7fff)<<16)|struct.unpack_from('<H',chunk,p)[0];p+=2
            out.append(chunk[p:p+2*n].decode('utf-16le','replace'))
    return out,size

def inspect(apk):
    with zipfile.ZipFile(apk) as z: data=z.read('AndroidManifest.xml')
    typ,hs,total=struct.unpack_from('<HHI',data,0)
    if typ!=3: raise ValueError('AndroidManifest.xml non binaire')
    pos=hs
    if struct.unpack_from('<H',data,pos)[0]!=1: raise ValueError('string pool attendu')
    _,_,pool_size=struct.unpack_from('<HHI',data,pos)
    strings,_=parse_pool(data[pos:pos+pool_size]);pos+=pool_size
    info={'package':'','versionCode':0,'versionName':'','minSdk':0,'targetSdk':0,'permissions':[],'providers':[]}
    while pos+8<=len(data):
        ctyp,chs,csize=struct.unpack_from('<HHI',data,pos)
        if csize<8 or pos+csize>len(data): break
        if ctyp==0x0102:
            line,comment,ns_idx,name_idx=struct.unpack_from('<IIII',data,pos+8)
            attr_start,attr_size,attr_count,ididx,classidx,styleidx=struct.unpack_from('<HHHHHH',data,pos+24)
            name=strings[name_idx] if name_idx!=NO and name_idx<len(strings) else ''
            attrs={}
            base=pos+16+attr_start
            for i in range(attr_count):
                aoff=base+i*attr_size
                ans,aname,raw,vsize,res0,vtype,val=struct.unpack_from('<IIIHBBI',data,aoff)
                key=strings[aname] if aname!=NO and aname<len(strings) else str(aname)
                if raw!=NO and raw<len(strings): value=strings[raw]
                elif vtype==0x03 and val<len(strings): value=strings[val]
                elif vtype in (0x10,0x11,0x12): value=val
                else: value=val
                attrs[key]=value
            if name=='manifest':
                info['package']=str(attrs.get('package',''));info['versionCode']=int(attrs.get('versionCode',0));info['versionName']=str(attrs.get('versionName',''))
            elif name=='uses-sdk':
                info['minSdk']=int(attrs.get('minSdkVersion',0));info['targetSdk']=int(attrs.get('targetSdkVersion',0))
            elif name=='uses-permission':
                n=attrs.get('name')
                if n: info['permissions'].append(str(n))
            elif name=='provider':
                info['providers'].append({'name':str(attrs.get('name','')),'authorities':str(attrs.get('authorities','')),'exported':bool(attrs.get('exported',0)),'grantUriPermissions':bool(attrs.get('grantUriPermissions',0))})
        pos+=csize
    return info

def main():
    if len(sys.argv)!=2: raise SystemExit('usage: read_apk_info.py file.apk')
    print(json.dumps(inspect(Path(sys.argv[1])),ensure_ascii=False,indent=2))
if __name__=='__main__': main()
