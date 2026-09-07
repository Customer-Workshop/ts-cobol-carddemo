# CardDemo core - generated call graph (do not edit; regenerate with build_call_graph.py)

Denominator: **33 program sources** in `app/cbl` + `app/asm`.

## Program -> program edges

| From | To | Kind | Cite | Resolution |
|---|---|---|---|---|
| CBACT01C | COBDATFT | CALL | `app/cbl/CBACT01C.cbl:231` | literal |
| CBACT01C | CEE3ABD | CALL | `app/cbl/CBACT01C.cbl:410` | literal |
| CBACT02C | CEE3ABD | CALL | `app/cbl/CBACT02C.cbl:158` | literal |
| CBACT03C | CEE3ABD | CALL | `app/cbl/CBACT03C.cbl:158` | literal |
| CBACT04C | CEE3ABD | CALL | `app/cbl/CBACT04C.cbl:632` | literal |
| CBCUS01C | CEE3ABD | CALL | `app/cbl/CBCUS01C.cbl:158` | literal |
| CBEXPORT | CEE3ABD | CALL | `app/cbl/CBEXPORT.cbl:579` | literal |
| CBIMPORT | CEE3ABD | CALL | `app/cbl/CBIMPORT.cbl:484` | literal |
| CBSTM03A | CBSTM03B | CALL | `app/cbl/CBSTM03A.CBL:351` | literal |
| CBSTM03A | CBSTM03B | CALL | `app/cbl/CBSTM03A.CBL:377` | literal |
| CBSTM03A | CBSTM03B | CALL | `app/cbl/CBSTM03A.CBL:401` | literal |
| CBSTM03A | CBSTM03B | CALL | `app/cbl/CBSTM03A.CBL:734` | literal |
| CBSTM03A | CBSTM03B | CALL | `app/cbl/CBSTM03A.CBL:746` | literal |
| CBSTM03A | CBSTM03B | CALL | `app/cbl/CBSTM03A.CBL:769` | literal |
| CBSTM03A | CBSTM03B | CALL | `app/cbl/CBSTM03A.CBL:787` | literal |
| CBSTM03A | CBSTM03B | CALL | `app/cbl/CBSTM03A.CBL:805` | literal |
| CBSTM03A | CBSTM03B | CALL | `app/cbl/CBSTM03A.CBL:835` | literal |
| CBSTM03A | CBSTM03B | CALL | `app/cbl/CBSTM03A.CBL:860` | literal |
| CBSTM03A | CBSTM03B | CALL | `app/cbl/CBSTM03A.CBL:877` | literal |
| CBSTM03A | CBSTM03B | CALL | `app/cbl/CBSTM03A.CBL:893` | literal |
| CBSTM03A | CBSTM03B | CALL | `app/cbl/CBSTM03A.CBL:909` | literal |
| CBSTM03A | CEE3ABD | CALL | `app/cbl/CBSTM03A.CBL:923` | literal |
| CBTRN01C | CEE3ABD | CALL | `app/cbl/CBTRN01C.cbl:473` | literal |
| CBTRN02C | CEE3ABD | CALL | `app/cbl/CBTRN02C.cbl:711` | literal |
| CBTRN03C | CEE3ABD | CALL | `app/cbl/CBTRN03C.cbl:630` | literal |
| COACTUPC | COACTUPC | XCTL(dyn) | `app/cbl/COACTUPC.cbl:956` | MOVE CDEMO-FROM-PROGRAM -> MOVE LIT-THISPGM -> VALUE clause @ app/cbl/COACTUPC.cbl:533 |
| COACTUPC | COMEN01C | XCTL(dyn) | `app/cbl/COACTUPC.cbl:956` | MOVE LIT-MENUPGM -> VALUE clause @ app/cbl/COACTUPC.cbl:557 |
| COACTVWC | COACTVWC | XCTL(dyn) | `app/cbl/COACTVWC.cbl:349` | MOVE CDEMO-FROM-PROGRAM -> MOVE LIT-THISPGM -> VALUE clause @ app/cbl/COACTVWC.cbl:143 |
| COACTVWC | COMEN01C | XCTL(dyn) | `app/cbl/COACTVWC.cbl:349` | MOVE LIT-MENUPGM -> VALUE clause @ app/cbl/COACTVWC.cbl:168 |
| COADM01C | COTRTLIC | XCTL(dyn) | `app/cbl/COADM01C.cbl:145` | option table entry @ app/cpy/COADM02Y.cpy:49 |
| COADM01C | COTRTUPC | XCTL(dyn) | `app/cbl/COADM01C.cbl:145` | option table entry @ app/cpy/COADM02Y.cpy:53 |
| COADM01C | COUSR00C | XCTL(dyn) | `app/cbl/COADM01C.cbl:145` | option table entry @ app/cpy/COADM02Y.cpy:29 |
| COADM01C | COUSR01C | XCTL(dyn) | `app/cbl/COADM01C.cbl:145` | option table entry @ app/cpy/COADM02Y.cpy:34 |
| COADM01C | COUSR02C | XCTL(dyn) | `app/cbl/COADM01C.cbl:145` | option table entry @ app/cpy/COADM02Y.cpy:39 |
| COADM01C | COUSR03C | XCTL(dyn) | `app/cbl/COADM01C.cbl:145` | option table entry @ app/cpy/COADM02Y.cpy:44 |
| COADM01C | COSGN00C | XCTL(dyn) | `app/cbl/COADM01C.cbl:168` | MOVE literal @ app/cbl/COADM01C.cbl:101 |
| COADM01C | COSGN00C | XCTL(dyn) | `app/cbl/COADM01C.cbl:168` | MOVE literal @ app/cbl/COADM01C.cbl:166 |
| COBIL00C | COMEN01C | XCTL(dyn) | `app/cbl/COBIL00C.cbl:281` | MOVE literal @ app/cbl/COBIL00C.cbl:130 |
| COBIL00C | COSGN00C | XCTL(dyn) | `app/cbl/COBIL00C.cbl:281` | MOVE literal @ app/cbl/COBIL00C.cbl:108 |
| COBIL00C | COSGN00C | XCTL(dyn) | `app/cbl/COBIL00C.cbl:281` | MOVE literal @ app/cbl/COBIL00C.cbl:276 |
| COBSWAIT | MVSWAIT | CALL | `app/cbl/COBSWAIT.cbl:38` | literal |
| COCRDLIC | COMEN01C | XCTL(dyn) | `app/cbl/COCRDLIC.cbl:402` | VALUE clause @ app/cbl/COCRDLIC.cbl:187 |
| COCRDLIC | COCRDLIC | XCTL(dyn) | `app/cbl/COCRDLIC.cbl:538` | MOVE LIT-THISPGM -> VALUE clause @ app/cbl/COCRDLIC.cbl:179 |
| COCRDLIC | COCRDSLC | XCTL(dyn) | `app/cbl/COCRDLIC.cbl:538` | MOVE LIT-CARDDTLPGM -> VALUE clause @ app/cbl/COCRDLIC.cbl:195 |
| COCRDLIC | COCRDUPC | XCTL(dyn) | `app/cbl/COCRDLIC.cbl:538` | MOVE LIT-CARDUPDPGM -> VALUE clause @ app/cbl/COCRDLIC.cbl:203 |
| COCRDLIC | COCRDLIC | XCTL(dyn) | `app/cbl/COCRDLIC.cbl:566` | MOVE LIT-THISPGM -> VALUE clause @ app/cbl/COCRDLIC.cbl:179 |
| COCRDLIC | COCRDSLC | XCTL(dyn) | `app/cbl/COCRDLIC.cbl:566` | MOVE LIT-CARDDTLPGM -> VALUE clause @ app/cbl/COCRDLIC.cbl:195 |
| COCRDLIC | COCRDUPC | XCTL(dyn) | `app/cbl/COCRDLIC.cbl:566` | MOVE LIT-CARDUPDPGM -> VALUE clause @ app/cbl/COCRDLIC.cbl:203 |
| COCRDSLC | COCRDSLC | XCTL(dyn) | `app/cbl/COCRDSLC.cbl:331` | MOVE CDEMO-FROM-PROGRAM -> MOVE LIT-THISPGM -> VALUE clause @ app/cbl/COCRDSLC.cbl:163 |
| COCRDSLC | COMEN01C | XCTL(dyn) | `app/cbl/COCRDSLC.cbl:331` | MOVE LIT-MENUPGM -> VALUE clause @ app/cbl/COCRDSLC.cbl:179 |
| COCRDUPC | COCRDUPC | XCTL(dyn) | `app/cbl/COCRDUPC.cbl:473` | MOVE CDEMO-FROM-PROGRAM -> MOVE LIT-THISPGM -> VALUE clause @ app/cbl/COCRDUPC.cbl:219 |
| COCRDUPC | COMEN01C | XCTL(dyn) | `app/cbl/COCRDUPC.cbl:473` | MOVE LIT-MENUPGM -> VALUE clause @ app/cbl/COCRDUPC.cbl:235 |
| COMEN01C | COACTUPC | XCTL(dyn) | `app/cbl/COMEN01C.cbl:156` | option table entry @ app/cpy/COMEN02Y.cpy:34 |
| COMEN01C | COACTVWC | XCTL(dyn) | `app/cbl/COMEN01C.cbl:156` | option table entry @ app/cpy/COMEN02Y.cpy:28 |
| COMEN01C | COBIL00C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:156` | option table entry @ app/cpy/COMEN02Y.cpy:83 |
| COMEN01C | COCRDLIC | XCTL(dyn) | `app/cbl/COMEN01C.cbl:156` | option table entry @ app/cpy/COMEN02Y.cpy:40 |
| COMEN01C | COCRDSLC | XCTL(dyn) | `app/cbl/COMEN01C.cbl:156` | option table entry @ app/cpy/COMEN02Y.cpy:46 |
| COMEN01C | COCRDUPC | XCTL(dyn) | `app/cbl/COMEN01C.cbl:156` | option table entry @ app/cpy/COMEN02Y.cpy:52 |
| COMEN01C | COPAUS0C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:156` | option table entry @ app/cpy/COMEN02Y.cpy:89 |
| COMEN01C | CORPT00C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:156` | option table entry @ app/cpy/COMEN02Y.cpy:77 |
| COMEN01C | COTRN00C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:156` | option table entry @ app/cpy/COMEN02Y.cpy:58 |
| COMEN01C | COTRN01C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:156` | option table entry @ app/cpy/COMEN02Y.cpy:64 |
| COMEN01C | COTRN02C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:156` | option table entry @ app/cpy/COMEN02Y.cpy:71 |
| COMEN01C | COACTUPC | XCTL(dyn) | `app/cbl/COMEN01C.cbl:184` | option table entry @ app/cpy/COMEN02Y.cpy:34 |
| COMEN01C | COACTVWC | XCTL(dyn) | `app/cbl/COMEN01C.cbl:184` | option table entry @ app/cpy/COMEN02Y.cpy:28 |
| COMEN01C | COBIL00C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:184` | option table entry @ app/cpy/COMEN02Y.cpy:83 |
| COMEN01C | COCRDLIC | XCTL(dyn) | `app/cbl/COMEN01C.cbl:184` | option table entry @ app/cpy/COMEN02Y.cpy:40 |
| COMEN01C | COCRDSLC | XCTL(dyn) | `app/cbl/COMEN01C.cbl:184` | option table entry @ app/cpy/COMEN02Y.cpy:46 |
| COMEN01C | COCRDUPC | XCTL(dyn) | `app/cbl/COMEN01C.cbl:184` | option table entry @ app/cpy/COMEN02Y.cpy:52 |
| COMEN01C | COPAUS0C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:184` | option table entry @ app/cpy/COMEN02Y.cpy:89 |
| COMEN01C | CORPT00C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:184` | option table entry @ app/cpy/COMEN02Y.cpy:77 |
| COMEN01C | COTRN00C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:184` | option table entry @ app/cpy/COMEN02Y.cpy:58 |
| COMEN01C | COTRN01C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:184` | option table entry @ app/cpy/COMEN02Y.cpy:64 |
| COMEN01C | COTRN02C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:184` | option table entry @ app/cpy/COMEN02Y.cpy:71 |
| COMEN01C | COSGN00C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:201` | MOVE literal @ app/cbl/COMEN01C.cbl:199 |
| COMEN01C | COSGN00C | XCTL(dyn) | `app/cbl/COMEN01C.cbl:201` | MOVE literal @ app/cbl/COMEN01C.cbl:97 |
| CORPT00C | CSUTLDTC | CALL | `app/cbl/CORPT00C.cbl:392` | literal |
| CORPT00C | CSUTLDTC | CALL | `app/cbl/CORPT00C.cbl:412` | literal |
| CORPT00C | COMEN01C | XCTL(dyn) | `app/cbl/CORPT00C.cbl:548` | MOVE literal @ app/cbl/CORPT00C.cbl:188 |
| CORPT00C | COSGN00C | XCTL(dyn) | `app/cbl/CORPT00C.cbl:548` | MOVE literal @ app/cbl/CORPT00C.cbl:173 |
| CORPT00C | COSGN00C | XCTL(dyn) | `app/cbl/CORPT00C.cbl:548` | MOVE literal @ app/cbl/CORPT00C.cbl:543 |
| COSGN00C | COADM01C | XCTL | `app/cbl/COSGN00C.cbl:231` | literal |
| COSGN00C | COMEN01C | XCTL | `app/cbl/COSGN00C.cbl:236` | literal |
| COTRN00C | COMEN01C | XCTL(dyn) | `app/cbl/COTRN00C.cbl:192` | MOVE literal @ app/cbl/COTRN00C.cbl:123 |
| COTRN00C | COSGN00C | XCTL(dyn) | `app/cbl/COTRN00C.cbl:192` | MOVE literal @ app/cbl/COTRN00C.cbl:108 |
| COTRN00C | COSGN00C | XCTL(dyn) | `app/cbl/COTRN00C.cbl:192` | MOVE literal @ app/cbl/COTRN00C.cbl:513 |
| COTRN00C | COTRN01C | XCTL(dyn) | `app/cbl/COTRN00C.cbl:192` | MOVE literal @ app/cbl/COTRN00C.cbl:188 |
| COTRN00C | COMEN01C | XCTL(dyn) | `app/cbl/COTRN00C.cbl:518` | MOVE literal @ app/cbl/COTRN00C.cbl:123 |
| COTRN00C | COSGN00C | XCTL(dyn) | `app/cbl/COTRN00C.cbl:518` | MOVE literal @ app/cbl/COTRN00C.cbl:108 |
| COTRN00C | COSGN00C | XCTL(dyn) | `app/cbl/COTRN00C.cbl:518` | MOVE literal @ app/cbl/COTRN00C.cbl:513 |
| COTRN00C | COTRN01C | XCTL(dyn) | `app/cbl/COTRN00C.cbl:518` | MOVE literal @ app/cbl/COTRN00C.cbl:188 |
| COTRN01C | COMEN01C | XCTL(dyn) | `app/cbl/COTRN01C.cbl:205` | MOVE literal @ app/cbl/COTRN01C.cbl:117 |
| COTRN01C | COSGN00C | XCTL(dyn) | `app/cbl/COTRN01C.cbl:205` | MOVE literal @ app/cbl/COTRN01C.cbl:200 |
| COTRN01C | COSGN00C | XCTL(dyn) | `app/cbl/COTRN01C.cbl:205` | MOVE literal @ app/cbl/COTRN01C.cbl:95 |
| COTRN01C | COTRN00C | XCTL(dyn) | `app/cbl/COTRN01C.cbl:205` | MOVE literal @ app/cbl/COTRN01C.cbl:126 |
| COTRN02C | CSUTLDTC | CALL | `app/cbl/COTRN02C.cbl:393` | literal |
| COTRN02C | CSUTLDTC | CALL | `app/cbl/COTRN02C.cbl:413` | literal |
| COTRN02C | COMEN01C | XCTL(dyn) | `app/cbl/COTRN02C.cbl:508` | MOVE literal @ app/cbl/COTRN02C.cbl:138 |
| COTRN02C | COSGN00C | XCTL(dyn) | `app/cbl/COTRN02C.cbl:508` | MOVE literal @ app/cbl/COTRN02C.cbl:116 |
| COTRN02C | COSGN00C | XCTL(dyn) | `app/cbl/COTRN02C.cbl:508` | MOVE literal @ app/cbl/COTRN02C.cbl:503 |
| COUSR00C | COADM01C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:196` | MOVE literal @ app/cbl/COUSR00C.cbl:126 |
| COUSR00C | COSGN00C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:196` | MOVE literal @ app/cbl/COUSR00C.cbl:111 |
| COUSR00C | COSGN00C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:196` | MOVE literal @ app/cbl/COUSR00C.cbl:509 |
| COUSR00C | COUSR02C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:196` | MOVE literal @ app/cbl/COUSR00C.cbl:192 |
| COUSR00C | COUSR03C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:196` | MOVE literal @ app/cbl/COUSR00C.cbl:202 |
| COUSR00C | COADM01C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:206` | MOVE literal @ app/cbl/COUSR00C.cbl:126 |
| COUSR00C | COSGN00C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:206` | MOVE literal @ app/cbl/COUSR00C.cbl:111 |
| COUSR00C | COSGN00C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:206` | MOVE literal @ app/cbl/COUSR00C.cbl:509 |
| COUSR00C | COUSR02C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:206` | MOVE literal @ app/cbl/COUSR00C.cbl:192 |
| COUSR00C | COUSR03C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:206` | MOVE literal @ app/cbl/COUSR00C.cbl:202 |
| COUSR00C | COADM01C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:514` | MOVE literal @ app/cbl/COUSR00C.cbl:126 |
| COUSR00C | COSGN00C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:514` | MOVE literal @ app/cbl/COUSR00C.cbl:111 |
| COUSR00C | COSGN00C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:514` | MOVE literal @ app/cbl/COUSR00C.cbl:509 |
| COUSR00C | COUSR02C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:514` | MOVE literal @ app/cbl/COUSR00C.cbl:192 |
| COUSR00C | COUSR03C | XCTL(dyn) | `app/cbl/COUSR00C.cbl:514` | MOVE literal @ app/cbl/COUSR00C.cbl:202 |
| COUSR01C | COADM01C | XCTL(dyn) | `app/cbl/COUSR01C.cbl:175` | MOVE literal @ app/cbl/COUSR01C.cbl:94 |
| COUSR01C | COSGN00C | XCTL(dyn) | `app/cbl/COUSR01C.cbl:175` | MOVE literal @ app/cbl/COUSR01C.cbl:168 |
| COUSR01C | COSGN00C | XCTL(dyn) | `app/cbl/COUSR01C.cbl:175` | MOVE literal @ app/cbl/COUSR01C.cbl:79 |
| COUSR02C | COADM01C | XCTL(dyn) | `app/cbl/COUSR02C.cbl:258` | MOVE literal @ app/cbl/COUSR02C.cbl:114 |
| COUSR02C | COADM01C | XCTL(dyn) | `app/cbl/COUSR02C.cbl:258` | MOVE literal @ app/cbl/COUSR02C.cbl:125 |
| COUSR02C | COSGN00C | XCTL(dyn) | `app/cbl/COUSR02C.cbl:258` | MOVE literal @ app/cbl/COUSR02C.cbl:253 |
| COUSR02C | COSGN00C | XCTL(dyn) | `app/cbl/COUSR02C.cbl:258` | MOVE literal @ app/cbl/COUSR02C.cbl:91 |
| COUSR03C | COADM01C | XCTL(dyn) | `app/cbl/COUSR03C.cbl:205` | MOVE literal @ app/cbl/COUSR03C.cbl:113 |
| COUSR03C | COADM01C | XCTL(dyn) | `app/cbl/COUSR03C.cbl:205` | MOVE literal @ app/cbl/COUSR03C.cbl:124 |
| COUSR03C | COSGN00C | XCTL(dyn) | `app/cbl/COUSR03C.cbl:205` | MOVE literal @ app/cbl/COUSR03C.cbl:200 |
| COUSR03C | COSGN00C | XCTL(dyn) | `app/cbl/COUSR03C.cbl:205` | MOVE literal @ app/cbl/COUSR03C.cbl:91 |
| CSUTLDTC | CEEDAYS | CALL | `app/cbl/CSUTLDTC.cbl:116` | literal |

## Unresolved / dynamic edges

| From | Variable | Kind | Cite | Note |
|---|---|---|---|---|

## JCL step -> program

| Job | Step | Program | Cite | Via |
|---|---|---|---|---|
| ACCTFILE | STEP05 | IDCAMS | `app/jcl/ACCTFILE.jcl:22` |  |
| ACCTFILE | STEP10 | IDCAMS | `app/jcl/ACCTFILE.jcl:33` |  |
| ACCTFILE | STEP15 | IDCAMS | `app/jcl/ACCTFILE.jcl:54` |  |
| CARDFILE | CLCIFIL | SDSF | `app/jcl/CARDFILE.jcl:22` |  |
| CARDFILE | STEP05 | IDCAMS | `app/jcl/CARDFILE.jcl:33` |  |
| CARDFILE | STEP10 | IDCAMS | `app/jcl/CARDFILE.jcl:47` |  |
| CARDFILE | STEP15 | IDCAMS | `app/jcl/CARDFILE.jcl:68` |  |
| CARDFILE | STEP40 | IDCAMS | `app/jcl/CARDFILE.jcl:80` |  |
| CARDFILE | STEP50 | IDCAMS | `app/jcl/CARDFILE.jcl:97` |  |
| CARDFILE | STEP60 | IDCAMS | `app/jcl/CARDFILE.jcl:107` |  |
| CARDFILE | OPCIFIL | SDSF | `app/jcl/CARDFILE.jcl:118` |  |
| CBADMCDJ | STEP1 | DFHCSDUP | `app/jcl/CBADMCDJ.jcl:27` |  |
| CBEXPORT | STEP01 | IDCAMS | `app/jcl/CBEXPORT.jcl:24` |  |
| CBEXPORT | STEP02 | CBEXPORT | `app/jcl/CBEXPORT.jcl:43` |  |
| CBIMPORT | STEP01 | CBIMPORT | `app/jcl/CBIMPORT.jcl:22` |  |
| CLOSEFIL | CLCIFIL | SDSF | `app/jcl/CLOSEFIL.jcl:22` |  |
| COMBTRAN | STEP05R | SORT | `app/jcl/COMBTRAN.jcl:22` |  |
| COMBTRAN | STEP10 | IDCAMS | `app/jcl/COMBTRAN.jcl:41` |  |
| CREASTMT | DELDEF01 | IDCAMS | `app/jcl/CREASTMT.JCL:22` |  |
| CREASTMT | STEP010 | SORT | `app/jcl/CREASTMT.JCL:44` |  |
| CREASTMT | STEP020 | IDCAMS | `app/jcl/CREASTMT.JCL:56` |  |
| CREASTMT | STEP030 | IEFBR14 | `app/jcl/CREASTMT.JCL:66` |  |
| CREASTMT | STEP040 | CBSTM03A | `app/jcl/CREASTMT.JCL:79` |  |
| CUSTFILE | CLCIFIL | SDSF | `app/jcl/CUSTFILE.jcl:22` |  |
| CUSTFILE | STEP05 | IDCAMS | `app/jcl/CUSTFILE.jcl:32` |  |
| CUSTFILE | STEP10 | IDCAMS | `app/jcl/CUSTFILE.jcl:43` |  |
| CUSTFILE | STEP15 | IDCAMS | `app/jcl/CUSTFILE.jcl:64` |  |
| CUSTFILE | OPCIFIL | SDSF | `app/jcl/CUSTFILE.jcl:76` |  |
| DALYREJS | STEP05 | IDCAMS | `app/jcl/DALYREJS.jcl:21` |  |
| DEFCUST | STEP05 | IDCAMS | `app/jcl/DEFCUST.jcl:22` |  |
| DEFCUST | STEP05 | IDCAMS | `app/jcl/DEFCUST.jcl:32` |  |
| DEFGDGB | STEP05 | IDCAMS | `app/jcl/DEFGDGB.jcl:21` |  |
| DEFGDGD | STEP10 | IDCAMS | `app/jcl/DEFGDGD.jcl:24` |  |
| DEFGDGD | STEP20 | IEBGENER | `app/jcl/DEFGDGD.jcl:36` |  |
| DEFGDGD | STEP30 | IDCAMS | `app/jcl/DEFGDGD.jcl:47` |  |
| DEFGDGD | STEP40 | IEBGENER | `app/jcl/DEFGDGD.jcl:59` |  |
| DEFGDGD | STEP50 | IDCAMS | `app/jcl/DEFGDGD.jcl:70` |  |
| DEFGDGD | STEP60 | IEBGENER | `app/jcl/DEFGDGD.jcl:82` |  |
| DISCGRP | STEP05 | IDCAMS | `app/jcl/DISCGRP.jcl:22` |  |
| DISCGRP | STEP10 | IDCAMS | `app/jcl/DISCGRP.jcl:33` |  |
| DISCGRP | STEP15 | IDCAMS | `app/jcl/DISCGRP.jcl:54` |  |
| DUSRSECJ | PREDEL | IEFBR14 | `app/jcl/DUSRSECJ.jcl:23` |  |
| DUSRSECJ | STEP01 | IEBGENER | `app/jcl/DUSRSECJ.jcl:32` |  |
| DUSRSECJ | STEP02 | IDCAMS | `app/jcl/DUSRSECJ.jcl:58` |  |
| DUSRSECJ | STEP03 | IDCAMS | `app/jcl/DUSRSECJ.jcl:80` |  |
| ESDSRRDS | PREDEL | IEFBR14 | `app/jcl/ESDSRRDS.jcl:24` |  |
| ESDSRRDS | STEP01 | IEBGENER | `app/jcl/ESDSRRDS.jcl:33` |  |
| ESDSRRDS | STEP02 | IDCAMS | `app/jcl/ESDSRRDS.jcl:59` |  |
| ESDSRRDS | STEP03 | IDCAMS | `app/jcl/ESDSRRDS.jcl:79` |  |
| ESDSRRDS | STEP04 | IDCAMS | `app/jcl/ESDSRRDS.jcl:93` |  |
| ESDSRRDS | STEP05 | IDCAMS | `app/jcl/ESDSRRDS.jcl:113` |  |
| FTPJCL | STEP1 | FTP | `app/jcl/FTPJCL.JCL:30` |  |
| INTCALC | STEP15 | CBACT04C | `app/jcl/INTCALC.jcl:22` |  |
| INTRDRJ1 | IDCAMS | IDCAMS | `app/jcl/INTRDRJ1.JCL:6` |  |
| INTRDRJ1 | STEP01 | IEBGENER | `app/jcl/INTRDRJ1.JCL:14` |  |
| INTRDRJ2 | IDCAMS | IDCAMS | `app/jcl/INTRDRJ2.JCL:7` |  |
| OPENFIL | OPCIFIL | SDSF | `app/jcl/OPENFIL.jcl:22` |  |
| POSTTRAN | STEP15 | CBTRN02C | `app/jcl/POSTTRAN.jcl:23` |  |
| PRTCATBL | DELDEF | IEFBR14 | `app/jcl/PRTCATBL.jcl:21` |  |
| PRTCATBL | STEP05R.PRC001 | IDCAMS | `app/proc/REPROC.prc:21` | PROC REPROC @ app/jcl/PRTCATBL.jcl:29 |
| PRTCATBL | STEP10R | SORT | `app/jcl/PRTCATBL.jcl:43` |  |
| READACCT | PREDEL | IEFBR14 | `app/jcl/READACCT.jcl:22` |  |
| READACCT | STEP05 | CBACT01C | `app/jcl/READACCT.jcl:32` |  |
| READCARD | STEP05 | CBACT02C | `app/jcl/READCARD.jcl:22` |  |
| READCUST | STEP05 | CBCUS01C | `app/jcl/READCUST.jcl:21` |  |
| READXREF | STEP05 | CBACT03C | `app/jcl/READXREF.jcl:22` |  |
| REPTFILE | STEP05 | IDCAMS | `app/jcl/REPTFILE.jcl:22` |  |
| TCATBALF | STEP05 | IDCAMS | `app/jcl/TCATBALF.jcl:22` |  |
| TCATBALF | STEP10 | IDCAMS | `app/jcl/TCATBALF.jcl:33` |  |
| TCATBALF | STEP15 | IDCAMS | `app/jcl/TCATBALF.jcl:54` |  |
| TRANBKP | STEP05R.PRC001 | IDCAMS | `app/proc/REPROC.prc:21` | PROC REPROC @ app/jcl/TRANBKP.jcl:23 |
| TRANBKP | STEP05 | IDCAMS | `app/jcl/TRANBKP.jcl:37` |  |
| TRANBKP | STEP10 | IDCAMS | `app/jcl/TRANBKP.jcl:51` |  |
| TRANCATG | STEP05 | IDCAMS | `app/jcl/TRANCATG.jcl:22` |  |
| TRANCATG | STEP10 | IDCAMS | `app/jcl/TRANCATG.jcl:33` |  |
| TRANCATG | STEP15 | IDCAMS | `app/jcl/TRANCATG.jcl:54` |  |
| TRANFILE | CLCIFIL | SDSF | `app/jcl/TRANFILE.jcl:22` |  |
| TRANFILE | STEP05 | IDCAMS | `app/jcl/TRANFILE.jcl:32` |  |
| TRANFILE | STEP10 | IDCAMS | `app/jcl/TRANFILE.jcl:46` |  |
| TRANFILE | STEP15 | IDCAMS | `app/jcl/TRANFILE.jcl:67` |  |
| TRANFILE | STEP20 | IDCAMS | `app/jcl/TRANFILE.jcl:79` |  |
| TRANFILE | STEP25 | IDCAMS | `app/jcl/TRANFILE.jcl:96` |  |
| TRANFILE | STEP30 | IDCAMS | `app/jcl/TRANFILE.jcl:106` |  |
| TRANFILE | OPCIFIL | SDSF | `app/jcl/TRANFILE.jcl:116` |  |
| TRANIDX | STEP20 | IDCAMS | `app/jcl/TRANIDX.jcl:22` |  |
| TRANIDX | STEP25 | IDCAMS | `app/jcl/TRANIDX.jcl:39` |  |
| TRANIDX | STEP30 | IDCAMS | `app/jcl/TRANIDX.jcl:49` |  |
| TRANREPT | STEP05R.PRC001 | IDCAMS | `app/proc/REPROC.prc:21` | PROC REPROC @ app/jcl/TRANREPT.jcl:23 |
| TRANREPT | STEP05R | SORT | `app/jcl/TRANREPT.jcl:37` |  |
| TRANREPT | STEP10R | CBTRN03C | `app/jcl/TRANREPT.jcl:59` |  |
| TRANTYPE | STEP05 | IDCAMS | `app/jcl/TRANTYPE.jcl:22` |  |
| TRANTYPE | STEP10 | IDCAMS | `app/jcl/TRANTYPE.jcl:33` |  |
| TRANTYPE | STEP15 | IDCAMS | `app/jcl/TRANTYPE.jcl:54` |  |
| TXT2PDF1 | TXT2PDF | IKJEFT1B | `app/jcl/TXT2PDF1.JCL:24` |  |
| WAITSTEP | WAIT | COBSWAIT | `app/jcl/WAITSTEP.jcl:22` |  |
| XREFFILE | STEP05 | IDCAMS | `app/jcl/XREFFILE.jcl:22` |  |
| XREFFILE | STEP10 | IDCAMS | `app/jcl/XREFFILE.jcl:36` |  |
| XREFFILE | STEP15 | IDCAMS | `app/jcl/XREFFILE.jcl:57` |  |
| XREFFILE | STEP20 | IDCAMS | `app/jcl/XREFFILE.jcl:69` |  |
| XREFFILE | STEP25 | IDCAMS | `app/jcl/XREFFILE.jcl:87` |  |
| XREFFILE | STEP30 | IDCAMS | `app/jcl/XREFFILE.jcl:97` |  |

## CICS transactions (CSD)

| Trancode | Program | Source present | Cite |
|---|---|---|---|
| CA00 | COADM01C | yes | `app/csd/CARDDEMO.CSD:327` |
| CAUP | COACTUPC | yes | `app/csd/CARDDEMO.CSD:306` |
| CAVW | COACTVWC | yes | `app/csd/CARDDEMO.CSD:317` |
| CB00 | COBIL00C | yes | `app/csd/CARDDEMO.CSD:337` |
| CC00 | COSGN00C | yes | `app/csd/CARDDEMO.CSD:378` |
| CCDL | COCRDSLC | yes | `app/csd/CARDDEMO.CSD:347` |
| CCLI | COCRDLIC | yes | `app/csd/CARDDEMO.CSD:357` |
| CCUP | COCRDUPC | yes | `app/csd/CARDDEMO.CSD:367` |
| CDV1 | COCRDSEC | NO | `app/csd/CARDDEMO.CSD:388` |
| CM00 | COMEN01C | yes | `app/csd/CARDDEMO.CSD:399` |
| CR00 | CORPT00C | yes | `app/csd/CARDDEMO.CSD:409` |
| CT00 | COTRN00C | yes | `app/csd/CARDDEMO.CSD:419` |
| CT01 | COTRN01C | yes | `app/csd/CARDDEMO.CSD:429` |
| CT02 | COTRN02C | yes | `app/csd/CARDDEMO.CSD:439` |
| CU00 | COUSR00C | yes | `app/csd/CARDDEMO.CSD:449` |
| CU01 | COUSR01C | yes | `app/csd/CARDDEMO.CSD:459` |
| CU02 | COUSR02C | yes | `app/csd/CARDDEMO.CSD:469` |
| CU03 | COUSR03C | yes | `app/csd/CARDDEMO.CSD:479` |

## Control-M jobs

| Folder | Job | MEMNAME | JCL present | IN conds | OUT conds | Cite |
|---|---|---|---|---|---|---|
| DAILY-TransactionBackup | CLOSEFIL | CLOSEFIL | yes | - | DAILY-TransactionBackup-CLOSEFIL | `app/scheduler/CardDemo.controlm:4` |
| DAILY-TransactionBackup | TRANBKP | TRANBKP | yes | DAILY-TransactionBackup-CLOSEFIL | DAILY-TransactionBackup-TRANBKP | `app/scheduler/CardDemo.controlm:8` |
| DAILY-TransactionBackup | WAITSTEP | WAITSTEP | yes | DAILY-TransactionBackup-TRANBKP | DAILY-TransactionBackup-WAITSTEP | `app/scheduler/CardDemo.controlm:14` |
| DAILY-TransactionBackup | OPENFIL | OPENFIL | yes | DAILY-TransactionBackup-WAITSTEP | - | `app/scheduler/CardDemo.controlm:20` |
| WEEKLY-TransactionTypesDBRefresh | MNTTRDB2 | MNTTRDB2 | NO | - | WEEKLY-TransactionTypesDBRefresh-MNTTRDB2 | `app/scheduler/CardDemo.controlm:27` |
| WEEKLY-DisclosureGroupsRefresh | CLOSEFIL | CLOSEFIL | yes | WEEKLY-TransactionTypesDBRefresh-MNTTRDB2 | WEEKLY-DisclosureGroupsRefresh-CLOSEFIL | `app/scheduler/CardDemo.controlm:33` |
| WEEKLY-DisclosureGroupsRefresh | DISCGRP | DISCGRP | yes | WEEKLY-DisclosureGroupsRefresh-CLOSEFIL | WEEKLY-DisclosureGroupsRefresh-DISCGRP | `app/scheduler/CardDemo.controlm:38` |
| WEEKLY-DisclosureGroupsRefresh | WAITSTEP | WAITSTEP | yes | WEEKLY-DisclosureGroupsRefresh-DISCGRP | WEEKLY-DisclosureGroupsRefresh-WAITSTEP | `app/scheduler/CardDemo.controlm:44` |
| WEEKLY-DisclosureGroupsRefresh | OPENFIL | OPENFIL | yes | WEEKLY-DisclosureGroupsRefresh-WAITSTEP | - | `app/scheduler/CardDemo.controlm:50` |
| WEEKLY-TransactionTypesDBRefresh | TRANEXTR | TRANEXTR | NO | WEEKLY-TransactionTypesDBRefresh-MNTTRDB2 | - | `app/scheduler/CardDemo.controlm:58` |
| MONTHLY-InterestCalculation | CLOSEFIL | CLOSEFIL | yes | - | MONTHLY-InterestCalculation-CLOSEFIL | `app/scheduler/CardDemo.controlm:65` |
| MONTHLY-InterestCalculation | INTCALC | INTCALC | yes | MONTHLY-InterestCalculation-CLOSEFIL | MONTHLY-InterestCalculation-INTCALC | `app/scheduler/CardDemo.controlm:69` |
| MONTHLY-InterestCalculation | COMBTRAN | COMBTRAN | yes | MONTHLY-InterestCalculation-INTCALC | MONTHLY-InterestCalculation-COMBTRAN | `app/scheduler/CardDemo.controlm:75` |
| MONTHLY-InterestCalculation | WAITSTEP | WAITSTEP | yes | MONTHLY-InterestCalculation-COMBTRAN | MONTHLY-InterestCalculation-WAITSTEP | `app/scheduler/CardDemo.controlm:81` |
| MONTHLY-InterestCalculation | OPENFIL | OPENFIL | yes | MONTHLY-InterestCalculation-WAITSTEP | - | `app/scheduler/CardDemo.controlm:87` |

## Dataset access (EXEC CICS)

| Program | File | Verb | Cite |
|---|---|---|---|
| COACTUPC | CXACAIX | READ | `app/cbl/COACTUPC.cbl:3654` |
| COACTUPC | ACCTDAT | READ | `app/cbl/COACTUPC.cbl:3703` |
| COACTUPC | CUSTDAT | READ | `app/cbl/COACTUPC.cbl:3753` |
| COACTUPC | ACCTDAT | READ | `app/cbl/COACTUPC.cbl:3894` |
| COACTUPC | CUSTDAT | READ | `app/cbl/COACTUPC.cbl:3921` |
| COACTUPC | ACCTDAT | REWRITE | `app/cbl/COACTUPC.cbl:4065` |
| COACTUPC | CUSTDAT | REWRITE | `app/cbl/COACTUPC.cbl:4085` |
| COACTVWC | CXACAIX | READ | `app/cbl/COACTVWC.cbl:727` |
| COACTVWC | ACCTDAT | READ | `app/cbl/COACTVWC.cbl:776` |
| COACTVWC | CUSTDAT | READ | `app/cbl/COACTVWC.cbl:826` |
| COBIL00C | ACCTDAT | READ | `app/cbl/COBIL00C.cbl:345` |
| COBIL00C | ACCTDAT | REWRITE | `app/cbl/COBIL00C.cbl:379` |
| COBIL00C | CXACAIX | READ | `app/cbl/COBIL00C.cbl:410` |
| COBIL00C | TRANSACT | STARTBR | `app/cbl/COBIL00C.cbl:443` |
| COBIL00C | TRANSACT | READPREV | `app/cbl/COBIL00C.cbl:474` |
| COBIL00C | TRANSACT | WRITE | `app/cbl/COBIL00C.cbl:512` |
| COCRDLIC | CARDDAT | STARTBR | `app/cbl/COCRDLIC.cbl:1129` |
| COCRDLIC | CARDDAT | READNEXT | `app/cbl/COCRDLIC.cbl:1146` |
| COCRDLIC | CARDDAT | READNEXT | `app/cbl/COCRDLIC.cbl:1197` |
| COCRDLIC | CARDDAT | STARTBR | `app/cbl/COCRDLIC.cbl:1273` |
| COCRDLIC | CARDDAT | READPREV | `app/cbl/COCRDLIC.cbl:1294` |
| COCRDLIC | CARDDAT | READPREV | `app/cbl/COCRDLIC.cbl:1322` |
| COCRDSLC | CARDAIX | READ | `app/cbl/COCRDSLC.cbl:742` |
| COCRDSLC | CARDDAT | READ | `app/cbl/COCRDSLC.cbl:742` |
| COCRDSLC | CARDAIX | READ | `app/cbl/COCRDSLC.cbl:783` |
| COCRDUPC | CARDAIX | READ | `app/cbl/COCRDUPC.cbl:1382` |
| COCRDUPC | CARDDAT | READ | `app/cbl/COCRDUPC.cbl:1382` |
| COCRDUPC | CARDAIX | READ | `app/cbl/COCRDUPC.cbl:1427` |
| COCRDUPC | CARDDAT | READ | `app/cbl/COCRDUPC.cbl:1427` |
| COCRDUPC | CARDAIX | REWRITE | `app/cbl/COCRDUPC.cbl:1477` |
| COCRDUPC | CARDDAT | REWRITE | `app/cbl/COCRDUPC.cbl:1477` |
| COSGN00C | USRSEC | READ | `app/cbl/COSGN00C.cbl:211` |
| COTRN00C | TRANSACT | STARTBR | `app/cbl/COTRN00C.cbl:593` |
| COTRN00C | TRANSACT | READNEXT | `app/cbl/COTRN00C.cbl:626` |
| COTRN00C | TRANSACT | READPREV | `app/cbl/COTRN00C.cbl:660` |
| COTRN01C | TRANSACT | READ | `app/cbl/COTRN01C.cbl:269` |
| COTRN02C | CXACAIX | READ | `app/cbl/COTRN02C.cbl:578` |
| COTRN02C | CCXREF | READ | `app/cbl/COTRN02C.cbl:611` |
| COTRN02C | TRANSACT | STARTBR | `app/cbl/COTRN02C.cbl:644` |
| COTRN02C | TRANSACT | READPREV | `app/cbl/COTRN02C.cbl:675` |
| COTRN02C | TRANSACT | WRITE | `app/cbl/COTRN02C.cbl:713` |
| COUSR00C | USRSEC | STARTBR | `app/cbl/COUSR00C.cbl:588` |
| COUSR00C | USRSEC | READNEXT | `app/cbl/COUSR00C.cbl:621` |
| COUSR00C | USRSEC | READPREV | `app/cbl/COUSR00C.cbl:655` |
| COUSR01C | USRSEC | WRITE | `app/cbl/COUSR01C.cbl:240` |
| COUSR02C | USRSEC | READ | `app/cbl/COUSR02C.cbl:322` |
| COUSR02C | USRSEC | REWRITE | `app/cbl/COUSR02C.cbl:360` |
| COUSR03C | USRSEC | READ | `app/cbl/COUSR03C.cbl:269` |
| COUSR03C | USRSEC | DELETE | `app/cbl/COUSR03C.cbl:307` |

## RETURN TRANSID

| Program | TRANSID | Cite |
|---|---|---|
| COACTUPC | CAUP | `app/cbl/COACTUPC.cbl:1015` |
| COACTVWC | CAVW | `app/cbl/COACTVWC.cbl:402` |
| COADM01C | CA00 | `app/cbl/COADM01C.cbl:111` |
| COADM01C | CA00 | `app/cbl/COADM01C.cbl:280` |
| COBIL00C | CB00 | `app/cbl/COBIL00C.cbl:146` |
| COCRDLIC | CCLI | `app/cbl/COCRDLIC.cbl:615` |
| COCRDSLC | CCDL | `app/cbl/COCRDSLC.cbl:402` |
| COCRDUPC | CCUP | `app/cbl/COCRDUPC.cbl:554` |
| COMEN01C | CM00 | `app/cbl/COMEN01C.cbl:107` |
| CORPT00C | CR00 | `app/cbl/CORPT00C.cbl:199` |
| CORPT00C | CR00 | `app/cbl/CORPT00C.cbl:587` |
| COSGN00C | CC00 | `app/cbl/COSGN00C.cbl:98` |
| COTRN00C | CT00 | `app/cbl/COTRN00C.cbl:138` |
| COTRN01C | CT01 | `app/cbl/COTRN01C.cbl:136` |
| COTRN02C | CT02 | `app/cbl/COTRN02C.cbl:156` |
| COTRN02C | CT02 | `app/cbl/COTRN02C.cbl:530` |
| COUSR00C | CU00 | `app/cbl/COUSR00C.cbl:141` |
| COUSR01C | CU01 | `app/cbl/COUSR01C.cbl:107` |
| COUSR02C | CU02 | `app/cbl/COUSR02C.cbl:135` |
| COUSR03C | CU03 | `app/cbl/COUSR03C.cbl:134` |

## TD queue writes (internal reader)

- CORPT00C -> TDQ `JOBS` (`app/cbl/CORPT00C.cbl:517`)

## Findings

- **csd_program_without_source**: COCRDSEC
- **orphan_roots_no_csd_no_jcl_no_caller**: CBTRN01C
- **call_targets_without_source**: COPAUS0C, COTRTLIC, COTRTUPC
- **controlm_memname_without_jcl**: MNTTRDB2, TRANEXTR
- **jcl_only_roots_not_scheduled**: ACCTFILE, CARDFILE, CBADMCDJ, CBEXPORT, CBIMPORT, CREASTMT, CUSTFILE, DALYREJS, DEFCUST, DEFGDGB, DEFGDGD, DUSRSECJ, ESDSRRDS, FTPJCL, INTRDRJ1, INTRDRJ2, POSTTRAN, PRTCATBL, READACCT, READCARD, READCUST, READXREF, REPTFILE, TCATBALF, TRANCATG, TRANFILE, TRANIDX, TRANREPT, TRANTYPE, TXT2PDF1, XREFFILE
