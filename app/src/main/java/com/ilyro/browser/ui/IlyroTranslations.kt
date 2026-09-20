package com.ilyro.browser.ui

/**
 * Compact UI translations for the globally supported ILYRO languages.
 *
 * The existing UI keeps English and Russian strings at the call sites. This
 * catalog adds the most visible navigation, settings, onboarding and browser
 * actions without changing that stable API. Unlisted long-tail messages fall
 * back to English until their wording is added here.
 */
private val translatedLanguages = listOf(
    AppLanguage.SPANISH,
    AppLanguage.CHINESE,
    AppLanguage.HINDI,
    AppLanguage.PORTUGUESE,
    AppLanguage.ARABIC,
    AppLanguage.FRENCH,
    AppLanguage.GERMAN,
    AppLanguage.JAPANESE,
    AppLanguage.KOREAN,
    AppLanguage.TURKISH,
    AppLanguage.ITALIAN,
    AppLanguage.INDONESIAN
)

private const val TRANSLATION_TABLE = """
System|Sistema|系统|सिस्टम|Sistema|النظام|Système|System|システム|시스템|Sistem|Sistema|Sistem
English|Inglés|英语|अंग्रेज़ी|Inglês|الإنجليزية|Anglais|Englisch|英語|영어|İngilizce|Inglese|Inggris
Russian|Ruso|俄语|रूसी|Russo|الروسية|Russe|Russisch|ロシア語|러시아어|Rusça|Russo|Rusia
Settings|Ajustes|设置|सेटिंग्स|Configurações|الإعدادات|Paramètres|Einstellungen|設定|설정|Ayarlar|Impostazioni|Pengaturan
Make ILYRO yours|Haz ILYRO tuyo|打造你的 ILYRO|ILYRO को अपना बनाएं|Personalize o ILYRO|اجعل ILYRO خاصًا بك|Faites d’ILYRO votre navigateur|Mach ILYRO zu deinem Browser|ILYROを自分好みに|ILYRO를 나만의 브라우저로|ILYRO’yu kendinize göre ayarlayın|Rendi ILYRO tuo|Jadikan ILYRO milik Anda
Account|Cuenta|账户|खाता|Conta|الحساب|Compte|Konto|アカウント|계정|Hesap|Account|Akun
General|General|常规|सामान्य|Geral|عام|Général|Allgemein|一般|일반|Genel|Generale|Umum
Appearance|Apariencia|外观|रूप-रंग|Aparência|المظهر|Apparence|Darstellung|外観|화면|Görünüm|Aspetto|Tampilan
Browsing|Navegación|浏览|ब्राउज़िंग|Navegação|التصفح|Navigation|Browser|ブラウジング|탐색|Gezinme|Navigazione|Penjelajahan
Privacy|Privacidad|隐私|गोपनीयता|Privacidade|الخصوصية|Confidentialité|Datenschutz|プライバシー|개인정보 보호|Gizlilik|Privacy|Privasi
Data|Datos|数据|डेटा|Dados|البيانات|Données|Daten|データ|데이터|Veriler|Dati|Data
About|Acerca de|关于|इसके बारे में|Sobre|حول|À propos|Über|アプリ情報|정보|Hakkında|Informazioni|Tentang
Developers|Desarrolladores|开发者|डेवलपर|Desenvolvedores|المطورون|Développeurs|Entwickler|開発者|개발자|Geliştiriciler|Sviluppatori|Pengembang
Application|Aplicación|应用|ऐप्लिकेशन|Aplicação|التطبيق|Application|Anwendung|アプリケーション|애플리케이션|Uygulama|Applicazione|Aplikasi
Support ILYRO|Apoyar ILYRO|支持 ILYRO|ILYRO का समर्थन करें|Apoiar o ILYRO|دعم ILYRO|Soutenir ILYRO|ILYRO unterstützen|ILYROを支援|ILYRO 후원|ILYRO’yu destekle|Sostieni ILYRO|Dukung ILYRO
Buy Me a Coffee|Invítame a un café|请我喝杯咖啡|मुझे कॉफ़ी खरीदें|Pague-me um café|اشترِ لي قهوة|Offrez-moi un café|Spendiere mir einen Kaffee|コーヒーをごちそう|커피 한 잔 후원|Bana bir kahve ısmarla|Offrimi un caffè|Belikan saya kopi
Legal|Información legal|法律信息|कानूनी जानकारी|Informações legais|المعلومات القانونية|Informations légales|Rechtliche Informationen|法的情報|법률 정보|Yasal bilgiler|Informazioni legali|Informasi hukum
Browser language|Idioma del navegador|浏览器语言|ब्राउज़र भाषा|Idioma do navegador|لغة المتصفح|Langue du navigateur|Browsersprache|ブラウザーの言語|브라우저 언어|Tarayıcı dili|Lingua del browser|Bahasa browser
Languages|Idiomas|语言|भाषाएं|Idiomas|اللغات|Langues|Sprachen|言語|언어|Diller|Lingue|Bahasa
Website languages|Idiomas de sitios web|网站语言|वेबसाइट भाषाएं|Idiomas dos sites|لغات المواقع|Langues des sites|Website-Sprachen|ウェブサイトの言語|웹사이트 언어|Web sitesi dilleri|Lingue dei siti|Bahasa situs
Add website languages|Añadir idiomas de sitios|添加网站语言|वेबसाइट भाषाएं जोड़ें|Adicionar idiomas de sites|إضافة لغات المواقع|Ajouter des langues de sites|Website-Sprachen hinzufügen|ウェブサイトの言語を追加|웹사이트 언어 추가|Web sitesi dili ekle|Aggiungi lingue dei siti|Tambahkan bahasa situs
+ Add language|+ Añadir idioma|+ 添加语言|+ भाषा जोड़ें|+ Adicionar idioma|+ إضافة لغة|+ Ajouter une langue|+ Sprache hinzufügen|+ 言語を追加|+ 언어 추가|+ Dil ekle|+ Aggiungi lingua|+ Tambah bahasa
Search languages|Buscar idiomas|搜索语言|भाषाएं खोजें|Pesquisar idiomas|البحث عن اللغات|Rechercher des langues|Sprachen suchen|言語を検索|언어 검색|Dil ara|Cerca lingue|Cari bahasa
Uses the supported language from Android; falls back to English.|Usa el idioma compatible de Android; si no está disponible, usa inglés.|使用 Android 支持的语言；不支持时使用英语。|Android की समर्थित भाषा का उपयोग करता है; उपलब्ध न होने पर अंग्रेज़ी।|Usa o idioma compatível do Android; usa inglês se não estiver disponível.|يستخدم اللغة المدعومة من Android؛ ويعود إلى الإنجليزية عند عدم توفرها.|Utilise la langue prise en charge par Android ; revient à l’anglais si elle n’est pas disponible.|Verwendet die von Android unterstützte Sprache; fällt sonst auf Englisch zurück.|Androidで対応している言語を使用し、未対応の場合は英語にします。|Android에서 지원되는 언어를 사용하며, 지원되지 않으면 영어를 사용합니다.|Android’un desteklediği dili kullanır; desteklenmiyorsa İngilizceye geçer.|Usa la lingua supportata da Android; in caso contrario usa l’inglese.|Menggunakan bahasa yang didukung Android; jika tidak tersedia, gunakan bahasa Inggris.
Sites receive these languages in this order. This does not require ILYRO itself to be translated into them.|Los sitios reciben estos idiomas en este orden. ILYRO no tiene que estar traducido a ellos.|网站会按此顺序接收这些语言。ILYRO 本身不必翻译成这些语言。|साइटों को ये भाषाएं इसी क्रम में मिलती हैं। इसके लिए ILYRO का इन भाषाओं में अनुवाद होना ज़रूरी नहीं है।|Os sites recebem estes idiomas nesta ordem. O ILYRO não precisa estar traduzido para eles.|تتلقى المواقع هذه اللغات بهذا الترتيب. لا يلزم ترجمة ILYRO إليها.|Les sites reçoivent ces langues dans cet ordre. ILYRO n’a pas besoin d’être traduit dans ces langues.|Websites erhalten diese Sprachen in dieser Reihenfolge. ILYRO selbst muss dafür nicht in diese Sprachen übersetzt sein.|サイトにはこの順序で言語が送信されます。ILYRO自体が翻訳されている必要はありません。|웹사이트에는 이 순서로 언어가 전달됩니다. ILYRO 자체가 해당 언어로 번역될 필요는 없습니다.|Siteler bu dilleri bu sırayla alır. ILYRO’nun bu dillere çevrilmiş olması gerekmez.|I siti ricevono queste lingue in quest’ordine. ILYRO non deve essere tradotto in queste lingue.|Situs menerima bahasa-bahasa ini dalam urutan ini. ILYRO sendiri tidak harus diterjemahkan ke bahasa tersebut.
Done|Listo|完成|हो गया|Concluído|تم|Terminé|Fertig|完了|완료|Bitti|Fatto|Selesai
Back|Atrás|返回|वापस|Voltar|رجوع|Retour|Zurück|戻る|뒤로|Geri|Indietro|Kembali
Forward|Adelante|前进|आगे|Avançar|تقدم|Suivant|Vorwärts|進む|앞으로|İleri|Avanti|Maju
More|Más|更多|अधिक|Mais|المزيد|Plus|Mehr|その他|더보기|Daha fazla|Altro|Lainnya
More options|Más opciones|更多选项|अधिक विकल्प|Mais opções|المزيد من الخيارات|Plus d’options|Weitere Optionen|その他のオプション|추가 옵션|Diğer seçenekler|Altre opzioni|Opsi lainnya
New tab|Nueva pestaña|新标签页|नया टैब|Nova aba|علامة تبويب جديدة|Nouvel onglet|Neuer Tab|新しいタブ|새 탭|Yeni sekme|Nuova scheda|Tab baru
New private tab|Nueva pestaña privada|新建隐私标签页|नया निजी टैब|Nova aba privada|علامة تبويب خاصة جديدة|Nouvel onglet privé|Neuer privater Tab|新しいプライベートタブ|새 비공개 탭|Yeni gizli sekme|Nuova scheda privata|Tab pribadi baru
Home|Inicio|主页|होम|Início|الرئيسية|Accueil|Startseite|ホーム|홈|Ana sayfa|Home|Beranda
Tabs|Pestañas|标签页|टैब|Abas|علامات التبويب|Onglets|Tabs|タブ|탭|Sekmeler|Schede|Tab
History|Historial|历史记录|इतिहास|Histórico|السجل|Historique|Verlauf|履歴|기록|Geçmiş|Cronologia|Riwayat
Bookmarks|Marcadores|书签|बुकमार्क|Favoritos|الإشارات المرجعية|Favoris|Lesezeichen|ブックマーク|북마크|Yer imleri|Segnalibri|Bookmark
Downloads|Descargas|下载|डाउनलोड|Downloads|التنزيلات|Téléchargements|Downloads|ダウンロード|다운로드|İndirilenler|Download|Unduhan
Share|Compartir|分享|शेयर|Compartilhar|مشاركة|Partager|Teilen|共有|공유|Paylaş|Condividi|Bagikan
Open|Abrir|打开|खोलें|Abrir|فتح|Ouvrir|Öffnen|開く|열기|Aç|Apri|Buka
Cancel|Cancelar|取消|रद्द करें|Cancelar|إلغاء|Annuler|Abbrechen|キャンセル|취소|İptal|Annulla|Batal
Save|Guardar|保存|सहेजें|Salvar|حفظ|Enregistrer|Speichern|保存|저장|Kaydet|Salva|Simpan
Close|Cerrar|关闭|बंद करें|Fechar|إغلاق|Fermer|Schließen|閉じる|닫기|Kapat|Chiudi|Tutup
Clear|Borrar|清除|साफ़ करें|Limpar|مسح|Effacer|Löschen|クリア|지우기|Temizle|Cancella|Hapus
Delete|Eliminar|删除|हटाएं|Excluir|حذف|Supprimer|Löschen|削除|삭제|Sil|Elimina|Hapus
Remove|Quitar|移除|हटाएं|Remover|إزالة|Supprimer|Entfernen|削除|제거|Kaldır|Rimuovi|Hapus
Edit|Editar|编辑|संपादित करें|Editar|تعديل|Modifier|Bearbeiten|編集|편집|Düzenle|Modifica|Edit
Search|Buscar|搜索|खोजें|Pesquisar|بحث|Rechercher|Suchen|検索|검색|Ara|Cerca|Cari
Reload|Recargar|重新加载|फिर से लोड करें|Recarregar|إعادة التحميل|Recharger|Neu laden|再読み込み|새로고침|Yenile|Ricarica|Muat ulang
Stop|Detener|停止|रोकें|Parar|إيقاف|Arrêter|Stopp|停止|중지|Durdur|Ferma|Hentikan
Translate page|Traducir página|翻译网页|पेज का अनुवाद करें|Traduzir página|ترجمة الصفحة|Traduire la page|Seite übersetzen|ページを翻訳|페이지 번역|Sayfayı çevir|Traduci pagina|Terjemahkan halaman
Reader mode|Modo lectura|阅读模式|रीडर मोड|Modo de leitura|وضع القراءة|Mode lecture|Lesemodus|リーダーモード|읽기 모드|Okuma modu|Modalità lettura|Mode pembaca
Download|Descargar|下载|डाउनलोड|Baixar|تنزيل|Télécharger|Herunterladen|ダウンロード|다운로드|İndir|Scarica|Unduh
Player|Reproductor|播放器|प्लेयर|Reprodutor|المشغل|Lecteur|Player|プレーヤー|플레이어|Oynatıcı|Lettore|Pemutar
Video|Vídeo|视频|वीडियो|Vídeo|فيديو|Vidéo|Video|動画|동영상|Video|Video|Video
Audio|Audio|音频|ऑडियो|Áudio|صوت|Audio|Audio|音声|오디오|Ses|Audio|Audio
All|Todo|全部|सभी|Todos|الكل|Tous|Alle|すべて|모두|Tümü|Tutti|Semua
Automatic sync|Sincronización automática|自动同步|ऑटोमैटिक सिंक|Sincronização automática|المزامنة التلقائية|Synchronisation automatique|Automatische Synchronisierung|自動同期|자동 동기화|Otomatik senkronizasyon|Sincronizzazione automatica|Sinkronisasi otomatis
Account & sync|Cuenta y sincronización|账户与同步|खाता और सिंक|Conta e sincronização|الحساب والمزامنة|Compte et synchronisation|Konto und Synchronisierung|アカウントと同期|계정 및 동기화|Hesap ve senkronizasyon|Account e sincronizzazione|Akun dan sinkronisasi
Sign in with Google|Iniciar sesión con Google|使用 Google 登录|Google से साइन इन करें|Entrar com o Google|تسجيل الدخول باستخدام Google|Se connecter avec Google|Mit Google anmelden|Googleでログイン|Google로 로그인|Google ile giriş yap|Accedi con Google|Masuk dengan Google
Sign out|Cerrar sesión|退出登录|साइन आउट|Sair|تسجيل الخروج|Se déconnecter|Abmelden|ログアウト|로그아웃|Çıkış yap|Esci|Keluar
Restore|Restaurar|恢复|पुनर्स्थापित करें|Restaurar|استعادة|Restaurer|Wiederherstellen|復元|복원|Geri yükle|Ripristina|Pulihkan
Delete cloud backup|Eliminar copia en la nube|删除云端备份|क्लाउड बैकअप हटाएं|Excluir backup na nuvem|حذف النسخة الاحتياطية السحابية|Supprimer la sauvegarde cloud|Cloud-Backup löschen|クラウドバックアップを削除|클라우드 백업 삭제|Bulut yedeğini sil|Elimina backup cloud|Hapus cadangan cloud
Protection|Protección|保护|सुरक्षा|Proteção|الحماية|Protection|Schutz|保護|보호|Koruma|Protezione|Perlindungan
Dark theme for websites|Tema oscuro para sitios web|网站深色主题|वेबसाइटों के लिए डार्क थीम|Tema escuro para sites|الوضع الداكن للمواقع|Thème sombre pour les sites|Dunkles Design für Websites|ウェブサイトのダークテーマ|웹사이트 어두운 테마|Web siteleri için koyu tema|Tema scuro per i siti|Tema gelap untuk situs
Theme|Tema|主题|थीम|Tema|السمة|Thème|Design|テーマ|테마|Tema|Tema|Tema
Wallpaper|Fondo de pantalla|壁纸|वॉलपेपर|Papel de parede|الخلفية|Fond d’écran|Hintergrundbild|壁紙|배경화면|Duvar kâğıdı|Sfondo|Wallpaper
Address bar|Barra de direcciones|地址栏|एड्रेस बार|Barra de endereço|شريط العنوان|Barre d’adresse|Adressleiste|アドレスバー|주소 표시줄|Adres çubuğu|Barra degli indirizzi|Bilah alamat
Default search engine|Motor de búsqueda predeterminado|默认搜索引擎|डिफ़ॉल्ट सर्च इंजन|Mecanismo de pesquisa padrão|محرك البحث الافتراضي|Moteur de recherche par défaut|Standardsuchmaschine|デフォルトの検索エンジン|기본 검색 엔진|Varsayılan arama motoru|Motore di ricerca predefinito|Mesin pencari default
Choose your look|Elige tu estilo|选择你的外观|अपना रूप चुनें|Escolha sua aparência|اختر مظهرك|Choisissez votre style|Wähle dein Aussehen|外観を選択|스타일 선택|Görünümünüzü seçin|Scegli il tuo stile|Pilih tampilan Anda
Start ILYRO|Iniciar ILYRO|开始使用 ILYRO|ILYRO शुरू करें|Iniciar o ILYRO|بدء ILYRO|Démarrer ILYRO|ILYRO starten|ILYROを始める|ILYRO 시작|ILYRO’yu başlat|Avvia ILYRO|Mulai ILYRO
Continue|Continuar|继续|जारी रखें|Continuar|متابعة|Continuer|Weiter|続ける|계속|Devam|Continua|Lanjutkan
Skip setup|Omitir configuración|跳过设置|सेटअप छोड़ें|Pular configuração|تخطي الإعداد|Ignorer la configuration|Einrichtung überspringen|設定をスキップ|설정 건너뛰기|Kurulumu atla|Salta configurazione|Lewati penyiapan
Ready|Listo|准备就绪|तैयार|Pronto|جاهز|Prêt|Bereit|準備完了|준비 완료|Hazır|Pronto|Siap
Privacy Policy|Política de privacidad|隐私政策|गोपनीयता नीति|Política de Privacidade|سياسة الخصوصية|Politique de confidentialité|Datenschutzerklärung|プライバシーポリシー|개인정보 처리방침|Gizlilik Politikası|Informativa sulla privacy|Kebijakan Privasi
Terms of Service|Términos del servicio|服务条款|सेवा की शर्तें|Termos de Serviço|شروط الخدمة|Conditions d’utilisation|Nutzungsbedingungen|利用規約|서비스 약관|Kullanım Koşulları|Termini di servizio|Ketentuan Layanan
Light|Claro|浅色|लाइट|Claro|فاتح|Clair|Hell|ライト|밝게|Açık|Chiaro|Terang
Dark|Oscuro|深色|डार्क|Escuro|داكن|Sombre|Dunkel|ダーク|어둡게|Koyu|Scuro|Gelap
Default|Predeterminado|默认|डिफ़ॉल्ट|Padrão|افتراضي|Par défaut|Standard|デフォルト|기본|Varsayılan|Predefinito|Default
Compact|Compacto|紧凑|कॉम्पैक्ट|Compacto|مضغوط|Compact|Kompakt|コンパクト|간결|Kompakt|Compatto|Ringkas
Normal|Normal|普通|सामान्य|Normal|عادي|Normal|Normal|標準|보통|Normal|Normale|Normal
Comfort|Cómodo|舒适|आरामदायक|Confortável|مريح|Confort|Komfortabel|快適|편안|Rahat|Comodo|Nyaman
Privacy & security|Privacidad y seguridad|隐私与安全|गोपनीयता और सुरक्षा|Privacidade e segurança|الخصوصية والأمان|Confidentialité et sécurité|Datenschutz und Sicherheit|プライバシーとセキュリティ|개인정보 및 보안|Gizlilik ve güvenlik|Privacy e sicurezza|Privasi dan keamanan
Site permissions|Permisos de sitios|网站权限|साइट अनुमतियां|Permissões de sites|أذونات المواقع|Autorisations des sites|Website-Berechtigungen|サイトの権限|사이트 권한|Site izinleri|Autorizzazioni dei siti|Izin situs
Downloads on mobile data|Descargas con datos móviles|使用移动数据下载|मोबाइल डेटा पर डाउनलोड|Downloads com dados móveis|التنزيل عبر بيانات الهاتف|Téléchargements avec données mobiles|Downloads über mobile Daten|モバイルデータでダウンロード|모바일 데이터로 다운로드|Mobil verilerle indirme|Download con dati mobili|Unduhan melalui data seluler
Link copied|Enlace copiado|链接已复制|लिंक कॉपी किया गया|Link copiado|تم نسخ الرابط|Lien copié|Link kopiert|リンクをコピーしました|링크가 복사되었습니다|Bağlantı kopyalandı|Link copiato|Tautan disalin
Share link|Compartir enlace|分享链接|लिंक शेयर करें|Compartilhar link|مشاركة الرابط|Partager le lien|Link teilen|リンクを共有|링크 공유|Bağlantıyı paylaş|Condividi link|Bagikan tautan
Find in page|Buscar en la página|在页面中查找|पेज में खोजें|Encontrar na página|البحث في الصفحة|Rechercher dans la page|Auf Seite suchen|ページ内検索|페이지에서 찾기|Sayfada bul|Trova nella pagina|Cari di halaman
Desktop site|Sitio para ordenador|桌面版网站|डेस्कटॉप साइट|Site para computador|موقع سطح المكتب|Version ordinateur|Desktopwebsite|PC版サイト|데스크톱 사이트|Masaüstü sitesi|Sito desktop|Situs desktop
Media|Multimedia|媒体|मीडिया|Mídia|الوسائط|Média|Medien|メディア|미디어|Medya|Media|Media
No downloads yet|Aún no hay descargas|暂无下载|अभी कोई डाउनलोड नहीं|Ainda não há downloads|لا توجد تنزيلات بعد|Aucun téléchargement|Noch keine Downloads|ダウンロードはまだありません|다운로드 없음|Henüz indirme yok|Nessun download|Belum ada unduhan
No bookmarks yet|Aún no hay marcadores|暂无书签|अभी कोई बुकमार्क नहीं|Ainda não há favoritos|لا توجد إشارات مرجعية بعد|Aucun favori|Noch keine Lesezeichen|ブックマークはまだありません|북마크 없음|Henüz yer imi yok|Nessun segnalibro|Belum ada bookmark
No history yet|Aún no hay historial|暂无历史记录|अभी कोई इतिहास नहीं|Ainda não há histórico|لا يوجد سجل بعد|Aucun historique|Noch kein Verlauf|履歴はまだありません|기록 없음|Henüz geçmiş yok|Nessuna cronologia|Belum ada riwayat
No saved passwords yet|Aún no hay contraseñas guardadas|暂无保存的密码|अभी कोई सहेजा हुआ पासवर्ड नहीं|Ainda não há senhas salvas|لا توجد كلمات مرور محفوظة بعد|Aucun mot de passe enregistré|Noch keine gespeicherten Passwörter|保存されたパスワードはありません|저장된 비밀번호 없음|Henüz kayıtlı şifre yok|Nessuna password salvata|Belum ada sandi tersimpan
Language, search and startup|Idioma, búsqueda e inicio|语言、搜索和启动|भाषा, खोज और स्टार्टअप|Idioma, pesquisa e inicialização|اللغة والبحث وبدء التشغيل|Langue, recherche et démarrage|Sprache, Suche und Start|言語・検索・起動|언어, 검색 및 시작|Dil, arama ve başlangıç|Lingua, ricerca e avvio|Bahasa, pencarian, dan mulai
Language, search engine and startup behavior.|Idioma, buscador y comportamiento de inicio.|语言、搜索引擎和启动行为。|भाषा, सर्च इंजन और स्टार्टअप व्यवहार।|Idioma, mecanismo de pesquisa e comportamento de inicialização.|اللغة ومحرك البحث وسلوك بدء التشغيل.|Langue, moteur de recherche et comportement au démarrage.|Sprache, Suchmaschine und Startverhalten.|言語、検索エンジン、起動動作。|언어, 검색 엔진 및 시작 동작|Dil, arama motoru ve başlangıç davranışı.|Lingua, motore di ricerca e comportamento all’avvio.|Bahasa, mesin pencari, dan perilaku mulai.
Browser toolbar|Barra del navegador|浏览器工具栏|ब्राउज़र टूलबार|Barra do navegador|شريط أدوات المتصفح|Barre du navigateur|Browser-Symbolleiste|ブラウザーツールバー|브라우저 도구 모음|Tarayıcı araç çubuğu|Barra del browser|Bilah alat browser
Quick access|Acceso rápido|快速访问|त्वरित पहुंच|Acesso rápido|الوصول السريع|Accès rapide|Schnellzugriff|クイックアクセス|빠른 액세스|Hızlı erişim|Accesso rapido|Akses cepat
Search engine|Motor de búsqueda|搜索引擎|सर्च इंजन|Mecanismo de pesquisa|محرك البحث|Moteur de recherche|Suchmaschine|検索エンジン|검색 엔진|Arama motoru|Motore di ricerca|Mesin pencari
Startup|Inicio|启动|स्टार्टअप|Inicialização|بدء التشغيل|Démarrage|Start|起動|시작|Başlangıç|Avvio|Mulai
Default browser|Navegador predeterminado|默认浏览器|डिफ़ॉल्ट ब्राउज़र|Navegador padrão|المتصفح الافتراضي|Navigateur par défaut|Standardbrowser|デフォルトブラウザ|기본 브라우저|Varsayılan tarayıcı|Browser predefinito|Browser default
Page behavior|Comportamiento de páginas|页面行为|पेज व्यवहार|Comportamento das páginas|سلوك الصفحات|Comportement des pages|Seitenverhalten|ページの動作|페이지 동작|Sayfa davranışı|Comportamento delle pagine|Perilaku halaman
Pages and downloads|Páginas y descargas|页面和下载|पेज और डाउनलोड|Páginas e downloads|الصفحات والتنزيلات|Pages et téléchargements|Seiten und Downloads|ページとダウンロード|페이지 및 다운로드|Sayfalar ve indirmeler|Pagine e download|Halaman dan unduhan
History and stored browser data management.|Gestión del historial y los datos guardados.|历史记录和浏览器数据管理。|इतिहास और सहेजे गए ब्राउज़र डेटा का प्रबंधन।|Gerenciamento do histórico e dos dados salvos.|إدارة السجل وبيانات المتصفح المحفوظة.|Gestion de l’historique et des données enregistrées.|Verwaltung von Verlauf und gespeicherten Browserdaten.|履歴と保存されたブラウザーデータの管理。|기록 및 저장된 브라우저 데이터 관리|Geçmiş ve kayıtlı tarayıcı verileri yönetimi.|Gestione della cronologia e dei dati salvati.|Pengelolaan riwayat dan data browser tersimpan.
Clear browser data|Borrar datos del navegador|清除浏览器数据|ब्राउज़र डेटा साफ़ करें|Limpar dados do navegador|مسح بيانات المتصفح|Effacer les données du navigateur|Browserdaten löschen|ブラウザーデータを消去|브라우저 데이터 지우기|Tarayıcı verilerini temizle|Cancella dati del browser|Hapus data browser
Data transfer|Transferencia de datos|数据传输|डेटा ट्रांसफर|Transferência de dados|نقل البيانات|Transfert de données|Datenübertragung|データ転送|데이터 전송|Veri aktarımı|Trasferimento dati|Transfer data
Passwords & import|Contraseñas e importación|密码和导入|पासवर्ड और इंपोर्ट|Senhas e importação|كلمات المرور والاستيراد|Mots de passe et importation|Passwörter und Import|パスワードとインポート|비밀번호 및 가져오기|Şifreler ve içe aktarma|Password e importazione|Sandi dan impor
App icon|Icono de la aplicación|应用图标|ऐप आइकन|Ícone do app|أيقونة التطبيق|Icône de l’application|App-Symbol|アプリアイコン|앱 아이콘|Uygulama simgesi|Icona dell’app|Ikon aplikasi
Interface density|Densidad de la interfaz|界面密度|इंटरफ़ेस घनत्व|Densidade da interface|كثافة الواجهة|Densité de l’interface|Oberflächendichte|インターフェース密度|인터페이스 밀도|Arayüz yoğunluğu|Densità dell’interfaccia|Kerapatan antarmuka
Text size|Tamaño del texto|文字大小|टेक्स्ट का आकार|Tamanho do texto|حجم النص|Taille du texte|Textgröße|文字サイズ|텍스트 크기|Metin boyutu|Dimensione del testo|Ukuran teks
Site data|Datos del sitio|网站数据|साइट डेटा|Dados do site|بيانات الموقع|Données du site|Websitedaten|サイトデータ|사이트 데이터|Site verileri|Dati del sito|Data situs
Permissions & site data|Permisos y datos del sitio|权限和网站数据|अनुमतियां और साइट डेटा|Permissões e dados do site|الأذونات وبيانات الموقع|Autorisations et données du site|Berechtigungen und Websitedaten|権限とサイトデータ|권한 및 사이트 데이터|İzinler ve site verileri|Autorizzazioni e dati del sito|Izin dan data situs
"""

private val translationCatalog: Map<AppLanguage, Map<String, String>> by lazy {
    val rows = TRANSLATION_TABLE.trim().lines()
        .filter { it.isNotBlank() }
        .map { it.split('|') }

    translatedLanguages.mapIndexed { index, language ->
        language to rows.mapNotNull { columns ->
            val english = columns.getOrNull(0)?.takeIf { it.isNotBlank() }
            val translated = columns.getOrNull(index + 1)?.takeIf { it.isNotBlank() }
            if (english == null || translated == null) null else english to translated
        }.toMap()
    }.toMap()
}

internal fun ilyroTranslation(language: AppLanguage, english: String): String? =
    translationCatalog[language]?.get(english)
