"""Bounded PNG structure, checksum and decompressed scanline validation."""
import struct
import zlib

def validate_png(raw):
    if raw[:8]!=b'\x89PNG\r\n\x1a\n' or len(raw)>1048576:raise ValueError('PNG signature/size')
    p=8;chunks=[];seen_end=False;header=None;compressed=[];palette=False;idat_ended=False
    while p<len(raw):
        if p+12>len(raw):raise ValueError('Truncated PNG')
        n=struct.unpack_from('>I',raw,p)[0];kind=raw[p+4:p+8]
        if p+12+n>len(raw):raise ValueError('Truncated chunk')
        data=raw[p+8:p+8+n];crc=struct.unpack_from('>I',raw,p+8+n)[0]
        if zlib.crc32(kind+data)&0xffffffff!=crc:raise ValueError('CRC mismatch')
        if not chunks and kind!=b'IHDR':raise ValueError('Missing IHDR')
        if kind==b'IHDR':
            if header is not None or n!=13:raise ValueError('IHDR')
            header=struct.unpack('>IIBBBBB',data)
        elif kind==b'IDAT':
            if idat_ended:raise ValueError('Nonconsecutive IDAT')
            compressed.append(data)
        else:
            if compressed:idat_ended=True
            if kind==b'PLTE':
                if not n or n%3 or n>768 or compressed:raise ValueError('PLTE')
                palette=True
            elif kind==b'IEND':
                if n!=0 or p+12!=len(raw):raise ValueError('IEND')
                seen_end=True
            elif kind[0]&32==0:raise ValueError('Unknown critical PNG chunk')
        chunks.append(kind);p+=12+n
    if not seen_end or not header or not compressed:raise ValueError('Incomplete PNG')
    w,h,depth,color,compression,filter_method,interlace=header
    depths={0:[1,2,4,8,16],2:[8,16],3:[1,2,4,8],4:[8,16],6:[8,16]}
    if min(w,h)<16 or max(w,h)>4096 or w*h>4194304 or depth not in depths.get(color,[]) or compression or filter_method or interlace not in (0,1):raise ValueError('Unsupported PNG geometry/format')
    if color==3 and not palette:raise ValueError('Missing palette')
    bits=depth*{0:1,2:3,3:1,4:2,6:4}[color]
    passes=[(0,0,1,1)] if not interlace else [(0,0,8,8),(4,0,8,8),(0,4,4,8),(2,0,4,4),(0,2,2,4),(1,0,2,2),(0,1,1,2)]
    rows=[]
    for x,y,dx,dy in passes:
        pw=max(0,(w-x+dx-1)//dx);ph=max(0,(h-y+dy-1)//dy)
        if pw and ph:rows.extend([(pw*bits+7)//8+1]*ph)
    expected=sum(rows)
    if expected>67108864:raise ValueError('PNG decoded size limit')
    decoder=zlib.decompressobj();pixels=decoder.decompress(b''.join(compressed),expected+1)
    if len(pixels)!=expected or not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:raise ValueError('Invalid PNG compressed data')
    offset=0
    for size in rows:
        if pixels[offset]>4:raise ValueError('Invalid PNG filter')
        offset+=size
    return w,h
