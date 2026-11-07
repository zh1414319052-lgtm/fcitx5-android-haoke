# 对应源码与数据 / Corresponding source and data

`manifest.json` 固定本版实际依赖的 12 份源码/字典归档，含原地址、提交、字节数和 SHA-256。归档保留在本地交付目录；公开版本将同样的原始文件作为 [haoke-v0.1.3.2 Release 附件](https://github.com/zh1414319052-lgtm/fcitx5-android-haoke/releases/tag/haoke-v0.1.3.2) 提供，避免把大归档嵌入每次 Git 克隆历史。这些是真实完整文件，不是 Git LFS 指针。

在仓库根目录运行：

```text
gh release download haoke-v0.1.3.2 --repo zh1414319052-lgtm/fcitx5-android-haoke --dir third-party/corresponding-source --skip-existing
```

或在 Release 页面逐项下载，按原文件名保存到本目录。核验 Python 3.11+ 示例：

```python
import hashlib, json
from pathlib import Path
root = Path('third-party/corresponding-source')
for item in json.loads((root / 'manifest.json').read_text(encoding='utf-8'))['archives']:
    path = root / item['file']
    with path.open('rb') as stream:
        assert path.stat().st_size == item['bytes']
        assert hashlib.file_digest(stream, 'sha256').hexdigest() == item['sha256'], path
print('All 12 corresponding-source/data archives verified.')
```

应用源码和 Git 子模块需另外递归检出。`prebuilder-eb156443-complete-source.tar.gz` 已包含对应 prebuilder 及递归子模块完整源码、许可、配方与补丁；源快照不含 `.git`，执行会重置 Git/应用补丁的原配方前，按 `docs/HAOKE_RELEASE.md` 初始化干净本地基线或直接递归检出固定上游提交。不得把本地临时基线当成真实上游提交。

The exact same 12 archives supplied locally are attached to the matching public Release. Keep their original filenames and verify every size and SHA-256 against `manifest.json`. The application repository and its pinned Git submodules are also required. The complete prebuilder archive includes recursive source trees, recipes, patches and licenses; see `docs/HAOKE_RELEASE.md` for reconstruction details and limits.
