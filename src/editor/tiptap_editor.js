/**
 * 위지윅 에디터 — tiptap.
 *
 * 설계 목표는 하나다: **컨트롤러·DTO 를 건드리지 않고 에디터를 붙인다.**
 * 원본 `<textarea>` 를 없애지 않고 숨긴 뒤 편집 결과를 그 value 로 되돌려 쓴다.
 * 폼 제출 경로는 그대로다.
 *
 * ── 마크업 계약 ──────────────────────────────────────────────────────────
 *   <textarea name="body" data-editor>...</textarea>            전역 기본 엔진
 *   <textarea name="body" data-editor="tiptap">...</textarea>   화면에서 엔진 지정
 *
 *   화면 지정(data-editor 값)이 전역 기본(window.PCMS_EDITOR_DEFAULT)보다 우선한다.
 *
 * ── 엔진 추가 ────────────────────────────────────────────────────────────
 *   현재 엔진은 tiptap 하나다. Namo CrossEditor 4 는 프로젝트 종료 후 도입한다(2026-07-31).
 *   그때는 같은 디렉터리에 `namo_editor.js` 를 만들고 `registerEngine('namo', …)` 로 등록한다.
 *   이 파일의 dispatch(마운트·폴백) 부분은 그 시점에 별도 코어로 분리하면 된다 —
 *   엔진이 하나뿐인 지금 레지스트리를 따로 두는 것은 과설계다.
 *
 * ── 동작 ────────────────────────────────────────────────────────────────
 *   1. `[data-editor]` 를 찾는다 (htmx 로 나중에 들어온 조각 포함)
 *   2. `data-initialized` 가드로 멱등 초기화한다
 *   3. 실패하면 **평문 폴백** — textarea 를 그대로 보여준다. 화면이 죽지 않는다
 *
 * ── 보안 ────────────────────────────────────────────────────────────────
 *   에디터 산출 HTML 은 신뢰 입력이 아니다. 저장 경로에서 서버가 OWASP Sanitizer 로
 *   반드시 정화한다(개발가이드 §10). 클라이언트 정화는 방어층이지 경계가 아니다.
 *   인라인 핸들러를 쓰지 않는다 — CSP 에 'unsafe-inline' 이 없어 조용히 무시된다.
 */
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';

const INIT_FLAG = 'data-initialized';
const DEFAULT_ENGINE = 'tiptap';

/** 엔진 이름 → 마운트 함수. Namo 도입 시 여기에 한 줄 추가한다. */
const ENGINES = Object.create(null);

export function registerEngine(name, mountFn) {
  ENGINES[name] = mountFn;
}

function resolveEngineName(el) {
  const explicit = (el.getAttribute('data-editor') || '').trim();
  if (explicit) return explicit;                        // 화면 지정이 최우선
  return window.PCMS_EDITOR_DEFAULT || DEFAULT_ENGINE;
}

/**
 * 평문 폴백 — 엔진 로드/초기화가 실패했을 때.
 * textarea 를 그대로 쓰게 두고 사용자에게 상태만 알린다.
 */
function fallbackToPlainText(textarea, reason) {
  textarea.hidden = false;
  textarea.removeAttribute('aria-hidden');
  textarea.setAttribute('data-editor-fallback', 'plain');

  const prev = textarea.previousElementSibling;
  if (!prev || !prev.hasAttribute('data-editor-notice')) {
    const notice = document.createElement('p');
    notice.setAttribute('data-editor-notice', '');
    notice.className = 'text-label-sm text-fg-muted mb-1';
    notice.textContent = '편집기를 불러오지 못해 일반 텍스트로 입력합니다. HTML 태그를 직접 쓸 수 있습니다.';
    textarea.parentNode.insertBefore(notice, textarea);
  }
  // 운영 원인 추적용. 사용자에게는 노출하지 않는다.
  console.warn('[editor] 폴백:', reason);
}

/**
 * 툴바 버튼 정의.
 * `cmd` 는 tiptap 체인 이름, `active` 는 현재 상태 판정용 인자다.
 * 전부 StarterKit 에 포함된 확장이라 추가 패키지가 필요 없다.
 */
const TOOLBAR = [
  { cmd: 'toggleBold',        label: '굵게',     text: 'B',  active: ['bold'] },
  { cmd: 'toggleItalic',      label: '기울임',   text: 'I',  active: ['italic'] },
  { cmd: 'toggleUnderline',   label: '밑줄',     text: 'U',  active: ['underline'] },
  { cmd: 'toggleStrike',      label: '취소선',   text: 'S',  active: ['strike'] },
  { sep: true },
  { cmd: 'toggleHeading',     label: '제목 1',   text: 'H1', arg: { level: 1 }, active: ['heading', { level: 1 }] },
  { cmd: 'toggleHeading',     label: '제목 2',   text: 'H2', arg: { level: 2 }, active: ['heading', { level: 2 }] },
  { cmd: 'toggleHeading',     label: '제목 3',   text: 'H3', arg: { level: 3 }, active: ['heading', { level: 3 }] },
  { sep: true },
  { cmd: 'toggleBulletList',  label: '글머리 목록', text: '•',  active: ['bulletList'] },
  { cmd: 'toggleOrderedList', label: '번호 목록',   text: '1.', active: ['orderedList'] },
  { cmd: 'toggleBlockquote',  label: '인용',     text: '❝',  active: ['blockquote'] },
  { cmd: 'toggleCodeBlock',   label: '코드 블록', text: '</>', active: ['codeBlock'] },
  { sep: true },
  { cmd: 'undo',              label: '실행 취소', text: '↶' },
  { cmd: 'redo',              label: '다시 실행', text: '↷' },
];

/**
 * 툴바 DOM 생성.
 * <b>인라인 핸들러를 쓰지 않는다</b> — CSP 에 'unsafe-inline' 이 없어 조용히 무시된다.
 * 툴바 컨테이너에 위임 리스너 하나만 건다.
 */
function buildToolbar(editor) {
  const bar = document.createElement('div');
  bar.className = 'pcms-editor__toolbar flex flex-wrap items-center gap-1 '
                + 'border-b border-line bg-surface-alt px-2 py-1';
  bar.setAttribute('role', 'toolbar');
  bar.setAttribute('aria-label', '서식 도구 모음');

  const buttons = [];
  TOOLBAR.forEach((item, i) => {
    if (item.sep) {
      const sep = document.createElement('span');
      sep.className = 'mx-1 h-4 w-px bg-gray-30';
      sep.setAttribute('aria-hidden', 'true');
      bar.appendChild(sep);
      return;
    }
    const b = document.createElement('button');
    b.type = 'button';                 // form 안이므로 submit 방지 필수
    b.dataset.editorCmd = String(i);   // 위임 핸들러가 인덱스로 정의를 찾는다
    b.title = item.label;
    b.setAttribute('aria-label', item.label);
    b.setAttribute('aria-pressed', 'false');
    b.className = 'min-w-8 px-2 py-1 rounded-small text-label-sm text-fg '
                + 'hover:bg-gray-10 focus:outline-2 focus:outline-brand-50';
    b.textContent = item.text;
    bar.appendChild(b);
    buttons.push(b);
  });

  // 위임 — 버튼마다 리스너를 달지 않는다.
  bar.addEventListener('click', (ev) => {
    const btn = ev.target.closest('[data-editor-cmd]');
    if (!btn) return;
    ev.preventDefault();
    const item = TOOLBAR[Number(btn.dataset.editorCmd)];
    if (!item || !item.cmd) return;
    const chain = editor.chain().focus();
    if (typeof chain[item.cmd] !== 'function') return;
    (item.arg ? chain[item.cmd](item.arg) : chain[item.cmd]()).run();
  });

  // 커서 위치에 따라 눌림 상태를 갱신한다 — 스크린리더에도 전달된다.
  const refresh = () => {
    buttons.forEach((b) => {
      const item = TOOLBAR[Number(b.dataset.editorCmd)];
      if (!item || !item.active) return;
      const on = editor.isActive(...item.active);
      b.setAttribute('aria-pressed', on ? 'true' : 'false');
      b.classList.toggle('bg-brand-5', on);
      b.classList.toggle('text-brand-50', on);
    });
  };
  editor.on('selectionUpdate', refresh);
  editor.on('transaction', refresh);
  return bar;
}

/** tiptap 마운트. */
function mountTiptap({ host, textarea, sync, initialValue, readOnly }) {
  // 툴바와 본문을 분리한다 — tiptap 은 element 를 통째로 차지한다.
  const content = document.createElement('div');

  const editor = new Editor({
    element: content,
    extensions: [StarterKit],
    content: initialValue || '',
    editable: !readOnly,
    editorProps: {
      attributes: {
        // KRDS 시맨틱 토큰만. raw hex·Tailwind 기본색 금지.
        class: 'pcms-editor__content min-h-48 p-3 outline-none focus:outline-2 focus:outline-brand-50',
        role: 'textbox',
        'aria-multiline': 'true',
        'aria-label': textarea.getAttribute('aria-label') || '본문 편집기',
      },
    },
    onUpdate: ({ editor: e }) => sync(e.getHTML()),
  });

  if (!readOnly) host.appendChild(buildToolbar(editor));
  host.appendChild(content);

  // 폼 제출 직전 마지막 상태를 확정한다.
  host.__pcmsFlush = () => sync(editor.getHTML());
  host.__pcmsDestroy = () => editor.destroy();
  return editor;
}

registerEngine('tiptap', mountTiptap);

/** textarea 하나를 에디터로 승격한다. 이미 초기화됐으면 아무것도 하지 않는다. */
function mount(textarea) {
  if (textarea.hasAttribute(INIT_FLAG)) return;
  textarea.setAttribute(INIT_FLAG, '');

  const name = resolveEngineName(textarea);
  const mountFn = ENGINES[name];

  if (!mountFn) {
    const known = Object.keys(ENGINES).join(', ') || '없음';
    fallbackToPlainText(textarea, `미등록 엔진 "${name}" (등록됨: ${known})`);
    return;
  }

  let host = null;
  try {
    host = document.createElement('div');
    host.className = 'pcms-editor border border-line rounded-medium bg-surface';
    host.setAttribute('data-editor-host', name);
    textarea.parentNode.insertBefore(host, textarea);

    // 원본 textarea 는 남긴다 — 폼 제출 값의 출처다.
    textarea.hidden = true;
    textarea.setAttribute('aria-hidden', 'true');

    mountFn({
      host,
      textarea,
      sync: (html) => { textarea.value = html; },
      initialValue: textarea.value,
      readOnly: textarea.hasAttribute('readonly'),
    });

    bindFormFlush(textarea);
  } catch (err) {
    if (host && host.parentNode) host.remove();
    fallbackToPlainText(textarea, err && err.message ? err.message : String(err));
  }
}

/**
 * 폼 제출 직전 모든 에디터의 최종 값을 textarea 로 밀어 넣는다.
 * onUpdate 가 디바운스되는 엔진에서도 마지막 입력을 놓치지 않는다.
 */
function bindFormFlush(textarea) {
  const form = textarea.closest('form');
  if (!form || form.hasAttribute('data-editor-submit-bound')) return;
  form.setAttribute('data-editor-submit-bound', '');
  form.addEventListener('submit', () => {
    form.querySelectorAll('[data-editor-host]').forEach((h) => {
      if (typeof h.__pcmsFlush === 'function') h.__pcmsFlush();
    });
  });
}

/** 문서(또는 주어진 루트) 안의 모든 `[data-editor]` 를 초기화한다. */
export function init(root) {
  (root || document).querySelectorAll('textarea[data-editor]').forEach(mount);
}

// 최초 로드 + htmx 로 들어온 조각 모두 처리한다.
// data-initialized 가드가 있어 중복 호출은 무해하다.
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => init());
} else {
  init();
}
document.addEventListener('htmx:load', (ev) => init(ev.target));
