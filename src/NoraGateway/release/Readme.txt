--------------------
NoraGateway
--------------------

【警告】
○このソフトウェアは開発段階で、不具合が頻発します
○このソフトウェアを使用して発生した不利益に対して作者は一切の責任を負いません
○不具合を通知された場合には直ちに使用を中止して下さい


【概要】
ID-51Plus2/ID-31Plus/ID-4100アクセスポイント・ターミナルモードから、日本国内レピータ、
並びにDExtra対応リフレクタ(XLX Multi Protoxol Reflectorなど)へ中継・制御するソフトウェアです。
(ICOM社製RS-MS3A/Wにリフレクター接続機能を追加したもの)

【機能】
○アクセスポイント/ターミナルモードから日本国内レピータへのゲート超え
○アクセスポイント/ターミナルモードからDExtra対応リフレクタへの接続
○定形・録音自動応答機能
○その他


【動作環境】
Windows 10 64bit & Java8 64bit
Ubuntu Linux 16.04LTS 64bit oracle jdk8
Raspberry Pi 3 Model B(Raspbian & oracle-java8-jdk)


【インストール】
1.Javaがインストールされていない場合にはインストールして下さい
https://java.com/ja/download/
2.ダウンロードしたソフトウェアをProgram Files(x86含む)[以外]へ解凍します
3.configフォルダの中にあるNoraGateway.xml.defaultをNoraGateway.xmlへコピーしてからファイル名を変更します
3.ファイル名を変更したNoraGateway.xmlをテキストエディタ等で開き、設定を編集します
4.UDPポート40000ポートを開放します(ルーター等の取扱説明書を参照下さい)


【起動】
start.batを実行して下さい

【終了】
コンソールで何かキーを押して下さい


【使用方法】
○RS-MS3A/Wと基本的には変わりません
○XLXリフレクタに接続する場合には、TOに「XRF000XL」と入力して送信します
例 XRF380のモジュールBに接続する場合
　TO「XRF380BL」
○逆に接続を解除する場合には、「_______U」(_はスペース7文字)か、
　無線機のTO SELECT画面からReflector→Unlink Reflectorを選択して送信します
○リフレクタで喋る時には、無線機のTO SELECT画面からReflector→Use Reflectorを選択して送信します


【リフレクタの追加】
configフォルダのhosts.txtを編集して下さい


【連絡先】
JQ1ZYC 圏央道友会 まいたけ吾郎
彩の國 日高市
kenoh_doyu@txb.sakura.ne.jp
※年間を通して社畜として飼われていますので返信は極めて遅れます。お察し下さい。



【ライセンス】
Copyright 2018 JQ1ZYC Goro Maitake

Permission is hereby granted, free of charge, to any person obtaining a copy of 
this software and associated documentation files (the "Software"), 
to deal in the Software without restriction, including without limitation 
the rights to use, copy, modify, merge, publish, distribute, sublicense, 
and/or sell copies of the Software, and to permit persons to whom the Software 
is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included 
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES 
OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, 
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE 
OR OTHER DEALINGS IN THE SOFTWARE.




Thw following sets forth attribution notices for thers party software that may be contained 
in portions of the this product.

-------------------------------------------------------------------------------

Apache Commons BeanUtils
Copyright 2000-2016 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (http://www.apache.org/).

-------------------------------------------------------------------------------

Apache Commons CLI
Copyright 2001-2017 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (http://www.apache.org/).

-------------------------------------------------------------------------------

Apache Commons Configuration
Copyright 2001-2017 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (http://www.apache.org/).

-------------------------------------------------------------------------------

Logback: the reliable, generic, fast and flexible logging framework.
Copyright (C) 1999-2017, QOS.ch. All rights reserved. 

This program and the accompanying materials are dual-licensed under
either the terms of the Eclipse Public License v1.0 as published by
the Eclipse Foundation
 
  or (per the licensee's choosing)
 
under the terms of the GNU Lesser General Public License version 2.1
as published by the Free Software Foundation.

-------------------------------------------------------------------------------

Fazecast jSerialComm
Copyright (C) 2012-2018 Fazecast, Inc.

This product includes software developed at Fazecast, Inc: you can
redistribute it and/or modify it under the terms of either the Apache
Software License, version 2, or the GNU Lesser General Public License
as published by the Free Software Foundation, version 3 or above.

jSerialComm is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
or FITNESS FOR A PARTICULAR PURPOSE.

You should have received a copy of both the GNU Lesser General Public
License and the Apache Software License along with jSerialComm. If not,
see <http://www.gnu.org/licenses/> and <http://www.apache.org/licenses/>.

-------------------------------------------------------------------------------

Copyright (C) 2009-2015 The Project Lombok Authors.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

-------------------------------------------------------------------------------

Java Native Access project (JNA) is dual-licensed under 2 
alternative Open Source/Free licenses: LGPL 2.1 or later and 
Apache License 2.0. (starting with JNA version 4.0.0). 

You can freely decide which license you want to apply to 
the project.

You may obtain a copy of the LGPL License at:

http://www.gnu.org/licenses/licenses.html

A copy is also included in the downloadable source code package
containing JNA, in file "LGPL2.1", under the same directory
as this file.

You may obtain a copy of the Apache License at:

http://www.apache.org/licenses/

A copy is also included in the downloadable source code package
containing JNA, in file "AL2.0", under the same directory
as this file.

-------------------------------------------------------------------------------

opus-java

Apache License 2.0

-------------------------------------------------------------------------------

Guava: Google Core Libraries for Java

Apache License 2.0

-------------------------------------------------------------------------------

 Copyright (c) 2004-2017 QOS.ch
 All rights reserved.

 Permission is hereby granted, free  of charge, to any person obtaining
 a  copy  of this  software  and  associated  documentation files  (the
 "Software"), to  deal in  the Software without  restriction, including
 without limitation  the rights to  use, copy, modify,  merge, publish,
 distribute,  sublicense, and/or sell  copies of  the Software,  and to
 permit persons to whom the Software  is furnished to do so, subject to
 the following conditions:
 
 The  above  copyright  notice  and  this permission  notice  shall  be
 included in all copies or substantial portions of the Software.
 
 THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 
-------------------------------------------------------------------------------
 
 Logback: the reliable, generic, fast and flexible logging framework.
Copyright (C) 1999-2017, QOS.ch. All rights reserved. 

This program and the accompanying materials are dual-licensed under
either the terms of the Eclipse Public License v1.0 as published by
the Eclipse Foundation
 
  or (per the licensee's choosing)
 
under the terms of the GNU Lesser General Public License version 2.1
as published by the Free Software Foundation.

-------------------------------------------------------------------------------

Gson

Apache License 2.0

-------------------------------------------------------------------------------

netty-socketio

Apache License 2.0

-------------------------------------------------------------------------------

pi4j-core

LGPL v3.0

-------------------------------------------------------------------------------

