// モックアップ（SinMemo_mockup.html）を本物のブラウザ（Chromium）で開き、ユーザーシナリオと仕様の手順を
// クリック・入力で再現して確かめる。ネットワークはすべて遮断し、外部への読み込みがないことも確かめる。
// 実行: node docs/mockup/verify_mockup.js [docs/mockup/SinMemo_mockup.html]
// 必要なもの: Node.js と Playwright（npm i -D playwright && npx playwright install chromium）
const path = require('path');
const { chromium } = require('playwright');

const file = path.resolve(process.argv[2] || path.join(__dirname, 'SinMemo_mockup.html'));
const url = 'file://' + file;
const results = [];
let current = null;

function check(cond, msg) { current.checks.push({ ok: !!cond, msg }); }

(async () => {
  const browser = await chromium.launch();
  const blocked = [];

  async function open(hash, opts) {
    const context = await browser.newContext(Object.assign({ viewport: { width: 1280, height: 900 }, offline: true }, opts || {}));
    // ファイル以外への通信はすべて止めて記録する（ネットなしで動くことの確認）
    await context.route('**/*', (route) => {
      const u = route.request().url();
      if (u.startsWith('file://')) return route.continue();
      blocked.push(u); return route.abort();
    });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.goto(url + (hash ? '#' + hash : ''));
    page.errors = errors;
    return { context, page };
  }

  async function scenario(id, title, hash, fn, opts) {
    current = { id, title, checks: [] };
    const { context, page } = await open(hash, opts);
    try { await fn(page); } catch (e) { current.checks.push({ ok: false, msg: '例外: ' + e.message.split('\n')[0] }); }
    check(page.errors.length === 0, 'スクリプトのエラーなし' + (page.errors.length ? '（' + page.errors.join(' / ') + '）' : ''));
    await context.close();
    results.push(current);
  }

  const S = '#screen';
  const btn = (p, label) => p.locator(S + ` [aria-label="${label}"]`);
  const textBtn = (p, text) => p.locator(S + ' button', { hasText: new RegExp('^' + text + '$') });
  const rows = (p) => p.locator(S + ' .row');
  const titles = (p) => p.locator(S + ' .row .t').allTextContents();
  const body = (p) => p.locator(S + ' textarea');
  const splashGone = (p) => p.waitForSelector('[data-testid="splash"]', { state: 'detached', timeout: 5000 });
  const panel = (p, id) => p.locator('#' + id);
  const isList = async (p) => (await p.locator(S + ' [data-testid="list"]').count()) === 1;
  const isEdit = async (p) => (await body(p).count()) === 1;
  const bg = (p) => p.locator('#phone').evaluate((el) => getComputedStyle(el).backgroundColor);

  // --- 起動 ---------------------------------------------------------------
  await scenario('起動', 'スプラッシュ（設定 基本設計 5 章）', '', async (p) => {
    const t0 = Date.now();
    check(await p.locator('[data-testid="splash"]').count() === 1, '開くとスプラッシュが出る');
    await splashGone(p);
    const ms = Date.now() - t0;
    check(ms >= 500, `最低 0.5 秒出ている（${ms} ミリ秒）`);
    check(ms < 2000, `起動から一覧まで 2 秒以内（${ms} ミリ秒）`);
    check(await isList(p), 'スプラッシュのあと一覧が出る');
  });

  // --- US-01 --------------------------------------------------------------
  await scenario('US-01', '思いついたことをメモする', 'list', async (p) => {
    const before = await rows(p).count();
    await btn(p, '新規作成').click();
    check(await isEdit(p) && (await body(p).inputValue()) === '', '「＋」で空の編集画面が開く');
    check(await p.evaluate(() => document.activeElement && document.activeElement.tagName) === 'TEXTAREA', '新規は入力欄にカーソルが入る（メモ編集 基本設計 決定事項 1）');
    check(await btn(p, 'メニュー').count() === 0, '新規では ⋮ を出さない');
    check(await btn(p, '保存').isDisabled(), '空のときは ✓ を押せない');
    await body(p).fill('　 \n ');
    check(await btn(p, '保存').isDisabled(), '空白・改行（全角スペースを含む）だけでも ✓ を押せない');
    await body(p).fill('電池を買う\n単3を4本');
    check(!(await btn(p, '保存').isDisabled()), '入力すると ✓ を押せる');
    await btn(p, '保存').click();
    check(await isList(p), '✓ で一覧に戻る');
    check(await rows(p).count() === before + 1, 'メモが 1 件増える');
    check((await titles(p))[0] === '電池を買う', '新しいメモが先頭に 1 行目で出る');
  });

  await scenario('US-01 例外', '保存せずに戻る（D-02）', 'list', async (p) => {
    const before = await rows(p).count();
    await btn(p, '新規作成').click();
    await btn(p, '戻る').click();
    check(await isList(p), '何も入力していなければ確認なしで一覧へ');
    await btn(p, '新規作成').click();
    await body(p).fill('途中まで');
    await btn(p, '戻る').click();
    check(await p.locator(S + ' [role="dialog"]', { hasText: '変更を破棄しますか？' }).count() === 1, '変更があると D-02 が出る');
    await textBtn(p, 'キャンセル').click();
    check(await isEdit(p) && (await body(p).inputValue()) === '途中まで', 'キャンセルで編集を続けられる');
    await p.keyboard.press('Escape');
    check(await p.locator(S + ' [role="dialog"]').count() === 1, 'Android の戻る操作でも D-02 が出る');
    await p.locator(S + ' .scrim').click({ position: { x: 10, y: 10 } });
    check(await p.locator(S + ' [role="dialog"]').count() === 0, 'ダイアログの外を押すと閉じる');
    await btn(p, '戻る').click();
    await textBtn(p, '破棄').click();
    check(await isList(p) && (await rows(p).count()) === before, '破棄で一覧へ。メモは増えない');
  });

  // --- US-02 --------------------------------------------------------------
  await scenario('US-02', '後で見返す（一覧の表示）', 'list', async (p) => {
    const t = await titles(p);
    const dates = await p.locator(S + ' .row .d').allTextContents();
    const year = new Date().getFullYear();
    check(t.includes('打ち合わせメモ'), '空行で始まるメモは最初の空でない行を出す');
    check(dates.some((d) => /^\d\d\/\d\d \d\d:\d\d$/.test(d)), '今年のメモは MM/dd HH:mm');
    check(dates.includes(`${year - 1}/12/28 21:04`), '去年のメモは yyyy/MM/dd HH:mm');
    check(t[t.length - 1] === '旅行の持ち物', '更新日時の新しい順（去年のメモが最後）');
    const overflow = await p.locator(S + ' .row .t').first().evaluate((el) => el.scrollWidth > el.clientWidth && getComputedStyle(el).textOverflow === 'ellipsis');
    check(overflow, '長い 1 行目は 1 行に収めて「…」');
    await p.locator(S + ' .row', { hasText: '打ち合わせメモ' }).click();
    check((await body(p).inputValue()).includes('議題: 来月の予定'), '押すと全文が出る');
  });

  // --- US-03 --------------------------------------------------------------
  await scenario('US-03', 'メモを直す', 'list', async (p) => {
    await p.locator(S + ' .row', { hasText: '旅行の持ち物' }).click();
    check(await p.evaluate(() => document.activeElement && document.activeElement.tagName) !== 'TEXTAREA', '既存のメモでは自動でカーソルを入れない');
    await btn(p, '保存').click();
    check((await titles(p)).slice(-1)[0] === '旅行の持ち物', '変更なしの ✓ では更新日時が変わらない（並びが変わらない）');
    await p.locator(S + ' .row', { hasText: '旅行の持ち物' }).click();
    await body(p).fill('旅行の持ち物（改）\n着替え');
    await btn(p, '保存').click();
    check((await titles(p))[0] === '旅行の持ち物（改）', '直して ✓ で先頭に来る（更新日時が今）');
  });

  // --- US-04 --------------------------------------------------------------
  await scenario('US-04', 'メモを消す（D-01）', 'list', async (p) => {
    const before = await rows(p).count();
    await rows(p).first().click();
    await btn(p, 'メニュー').click();
    await p.locator(S + ' [role="menuitem"]', { hasText: '削除' }).click();
    check(await p.locator(S + ' [role="dialog"]', { hasText: 'このメモを削除しますか？' }).count() === 1, '⋮ →「削除」で D-01');
    await textBtn(p, 'キャンセル').click();
    check(await isEdit(p), 'キャンセルで編集画面に残る');
    await btn(p, 'メニュー').click();
    await p.locator(S + ' [role="menuitem"]', { hasText: '削除' }).click();
    await textBtn(p, '削除').click();
    check(await isList(p) && (await rows(p).count()) === before - 1, '削除で一覧へ。1 件減る');
  });

  // --- US-05 --------------------------------------------------------------
  await scenario('US-05', 'アプリを閉じても次に開いたときメモと設定が残っている', 'list', async (p) => {
    await btn(p, '新規作成').click();
    await body(p).fill('残るはずのメモ');
    await btn(p, '保存').click();
    await btn(p, 'メニュー').click();
    await p.locator(S + ' [role="menuitem"]', { hasText: '設定' }).click();
    await p.locator(S + ' .setting', { hasText: '文字サイズ' }).click();
    await p.locator(S + ' [role="radio"]', { hasText: '大' }).click();
    await textBtn(p, 'OK').click();
    await panel(p, 'p-reboot').click();
    await panel(p, 'p-open').click();
    await splashGone(p);
    check(await isList(p) && (await titles(p))[0] === '残るはずのメモ', '端末を再起動して起動しても、保存したメモが残っている');
    check(await p.locator(S + ' .row .t').first().evaluate((el) => getComputedStyle(el).fontSize) === '20px', '設定（文字サイズ「大」）も残っている');
  });

  // --- 文字数の上限（メモ編集 基本設計 3.4） -----------------------------------------------
  await scenario('上限', '10,000 文字の上限と文字数の表示', 'long', async (p) => {
    const counter = p.locator('#counter');
    check(await counter.isVisible() && (await counter.textContent()) === '9,688/10,000文字', '9,500 文字以上で「9,688/10,000文字」の形で出る');
    await body(p).focus();
    await p.keyboard.press('Control+End');
    await p.keyboard.insertText('x'.repeat(400));
    check((await body(p).inputValue()).length === 9688, '上限を超える貼り付け（+400）は丸ごと取り消す');
    check(await p.locator(S + ' .snackbar', { hasText: '10,000文字を超えるため貼り付けられません' }).count() === 1, '2 文字以上の取り消しでメッセージ');
    await p.keyboard.insertText('y'.repeat(312));
    check((await body(p).inputValue()).length === 10000, 'ちょうど 10,000 文字になる貼り付けは入る');
    check(await counter.evaluate((el) => el.classList.contains('full')), '10,000 文字ちょうどで error の色');
    await p.keyboard.type('z');
    check((await body(p).inputValue()).length === 10000, '上限で 1 文字打っても入らない');
    await body(p).fill('短い');
    check(await counter.isHidden(), '9,499 文字以下では出さない');
  });

  // --- 設定（設定 基本設計） ------------------------------------------------------------
  await scenario('設定', '設定画面・D-03・D-04（OK で反映）', 'list', async (p) => {
    const lightBg = await bg(p);
    await btn(p, 'メニュー').click();
    await p.locator(S + ' [role="menuitem"]', { hasText: '設定' }).click();
    const values = await p.locator(S + ' .setting .v').allTextContents();
    check(values.join('|') === 'ライト|中|0.1.0', '初期値ライト・中、バージョン 0.1.0');
    await p.locator(S + ' .setting', { hasText: 'テーマ' }).click();
    check(await p.locator(S + ' [role="radio"][aria-checked="true"]').textContent() === 'ライト', 'D-03 は今の値に印');
    await p.locator(S + ' [role="radio"]', { hasText: 'ダーク' }).click();
    check(await bg(p) === lightBg, '選んだだけでは反映しない');
    await textBtn(p, 'キャンセル').click();
    check(await bg(p) === lightBg && (await p.locator(S + ' .setting .v').first().textContent()) === 'ライト', 'キャンセルでは変わらない');
    await p.locator(S + ' .setting', { hasText: 'テーマ' }).click();
    await p.locator(S + ' [role="radio"]', { hasText: 'ダーク' }).click();
    await textBtn(p, 'OK').click();
    check(await bg(p) === 'rgb(20, 21, 24)', 'OK でダーク（#141518）になる');
    await p.locator(S + ' .setting', { hasText: '文字サイズ' }).click();
    await p.locator(S + ' [role="radio"]', { hasText: '大' }).click();
    await textBtn(p, 'OK').click();
    await btn(p, '戻る').click();
    const size = await p.locator(S + ' .row .t').first().evaluate((el) => getComputedStyle(el).fontSize);
    const dsize = await p.locator(S + ' .row .d').first().evaluate((el) => getComputedStyle(el).fontSize);
    check(size === '20px' && dsize === '15px', '一覧の文字が「大」（20・15）');
    await rows(p).first().click();
    check(await body(p).evaluate((el) => getComputedStyle(el).fontSize) === '20px', '編集の本文も「大」（20）');
  });

  await scenario('端末に従う', 'テーマ「端末の設定に従う」が追従する', 'settings', async (p) => {
    await p.locator(S + ' .setting', { hasText: 'テーマ' }).click();
    await p.locator(S + ' [role="radio"]', { hasText: '端末の設定に従う' }).click();
    await textBtn(p, 'OK').click();
    check(await bg(p) === 'rgb(255, 255, 255)', '端末がライトならライト');
    await panel(p, 'p-dark').check();
    check(await bg(p) === 'rgb(20, 21, 24)', '端末をダークにすると開いたまま追従する');
  }, { colorScheme: 'light' });

  await scenario('端末に従う（ダーク端末）', 'ブラウザがダークなら最初から端末のダークとして扱う', 'settings', async (p) => {
    check(await panel(p, 'p-dark').isChecked(), 'ブラウザのダークモードを端末の設定として読む');
  }, { colorScheme: 'dark' });

  // --- US-06 下書き（下書き 基本設計） ------------------------------------------------------
  await scenario('US-06', 'システムに終了されたあと書きかけごと戻る', 'list', async (p) => {
    await p.locator(S + ' .row', { hasText: '旅行の持ち物' }).click();
    await body(p).fill('旅行の持ち物\n書きかけの行');
    await panel(p, 'p-home').click();
    check(await p.locator('[data-testid="home"]').count() === 1, 'ホームへ（裏に回る）');
    await panel(p, 'p-kill').click();
    await panel(p, 'p-open').click();
    await splashGone(p);
    check(await isEdit(p) && (await body(p).inputValue()) === '旅行の持ち物\n書きかけの行', '編集画面ごと書きかけが戻る');
    check(await p.evaluate(() => document.activeElement && document.activeElement.tagName) !== 'TEXTAREA', '戻ったときは自動でカーソルを入れない');
    await btn(p, '戻る').click();
    check(await p.locator(S + ' [role="dialog"]', { hasText: '変更を破棄しますか？' }).count() === 1, '元の本文と違うので ← で D-02');
    await textBtn(p, 'キャンセル').click();
    await panel(p, 'p-home').click();
    await panel(p, 'p-remove').click();
    await panel(p, 'p-open').click();
    await splashGone(p);
    check(await isList(p), '最近使ったアプリから消して起動すると一覧（書きかけは残らない）');
    check(!(await titles(p)).includes('書きかけの行'), '書きかけは保存されていない');
  });

  await scenario('US-06 例外', '元のメモが消えていたら書きかけで新規', 'list', async (p) => {
    // 一覧 → 編集（新規）で書きかけ → 裏 → 終了 → 戻る: 新規の書きかけが戻る
    await btn(p, '新規作成').click();
    await body(p).fill('新規の書きかけ');
    await panel(p, 'p-home').click();
    await panel(p, 'p-kill').click();
    await panel(p, 'p-open').click();
    await splashGone(p);
    check((await body(p).inputValue()) === '新規の書きかけ' && (await btn(p, 'メニュー').count()) === 0, '新規の書きかけが戻る（⋮ なし）');
  });

  await scenario('戻る', 'Android の戻る操作', 'settings', async (p) => {
    await p.keyboard.press('Escape');
    check(await isList(p), '設定で戻る → 一覧');
    await p.keyboard.press('Escape');
    check(await p.locator('[data-testid="home"]').count() === 1, '一覧で戻る → ホーム（裏に回る）');
    await panel(p, 'p-open').click();
    check(await isList(p) && (await p.locator('[data-testid="splash"]').count()) === 0, '裏から戻るとスプラッシュなしでそのまま');
  });

  await scenario('スマホ幅', '幅 390 の画面でも横にはみ出さない', 'list', async (p) => {
    const over = await p.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    check(!over, '横スクロールが出ない');
    check(await isList(p), '一覧が出る');
  }, { viewport: { width: 390, height: 844 } });

  await browser.close();

  let ok = 0, ng = 0;
  for (const r of results) {
    const bad = r.checks.filter((c) => !c.ok);
    ok += r.checks.length - bad.length; ng += bad.length;
    console.log(`${bad.length ? 'NG' : 'OK'}  ${r.id} ${r.title}（${r.checks.length - bad.length}/${r.checks.length}）`);
    for (const c of r.checks) if (!c.ok || process.env.VERBOSE) console.log(`      ${c.ok ? '○' : '×'} ${c.msg}`);
  }
  console.log(`外部への通信: ${blocked.length ? blocked.join(', ') : 'なし'}`);
  if (blocked.length) ng++;
  console.log(`合計: OK ${ok} / NG ${ng}`);
  process.exit(ng ? 1 : 0);
})();
