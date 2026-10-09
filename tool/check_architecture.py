from pathlib import Path
import re
import sys
from check_dashboard_architecture import check_dashboard

root = Path(__file__).resolve().parents[1]
errors = check_dashboard(root)
for source in root.rglob("*.kt"):
    if any(part in {"build", ".gradle", ".work", "node_modules"} for part in source.parts):
        continue
    if "/src/main/" not in str(source):
        continue
    relative = source.relative_to(root)
    text = source.read_text()
    if "/domain/" in str(relative):
        for forbidden in ("org.springframework", "org.jooq", "tools.jackson", "com.fasterxml.jackson", "jakarta.", "java.sql"):
            if re.search(r"^import\s+" + re.escape(forbidden), text, re.M):
                errors.append(f"{relative}: forbidden domain import {forbidden}")
    if "datasources" in source.parts:
        # Datasources return technical rows; entity/domain imports are not permitted.
        if re.search(r"^import\s+.*\.domain\.", text, re.M):
            errors.append(f"{relative}: datasource imports domain")
    if re.search(r"project\s*\(\s*[\"']", text):
        errors.append(f"{relative}: use type-safe project accessors")
for build in root.rglob("build.gradle.kts"):
    if any(p in {"build", ".gradle", "node_modules", ".work"} for p in build.parts):
        continue
    if re.search(r"project\s*\(\s*[\"']", build.read_text()):
        errors.append(f"{build.relative_to(root)}: use type-safe project accessors")
if errors:
    print("\n".join(errors))
    sys.exit(1)
print("Architecture checks passed")
