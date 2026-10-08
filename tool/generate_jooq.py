"""Generate query types from the real PostgreSQL migration schema."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import uuid
from xml.sax.saxutils import escape

classpath, destination, migration_dir = sys.argv[1:]
container = "hris-schema-" + uuid.uuid4().hex[:12]
docker = ["docker"]
if subprocess.run(docker + ["info"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
    docker = ["sudo", "-n", "docker"]

def run(*args, **kwargs):
    return subprocess.run(docker + list(args), check=True, **kwargs)

try:
    run("run", "--rm", "-d", "--name", container, "--label", "dev.fajar.hris=codegen",
        "-e", "POSTGRES_PASSWORD=codegen-only", "-e", "POSTGRES_DB=hris",
        "-p", "127.0.0.1::5432", "postgres:18.6-alpine", stdout=subprocess.DEVNULL)
    for attempt in range(90):
        if subprocess.run(docker + ["exec", "-e", "PGPASSWORD=codegen-only", container, "psql", "-h", "127.0.0.1", "-U", "postgres", "-d", "hris", "-c", "select 1"],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
            break
        time.sleep(0.25)
    else:
        raise RuntimeError("Temporary PostgreSQL did not start")
    for migration in sorted(Path(migration_dir).glob("V*__*.sql"), key=lambda p: int(p.name.split("__")[0][1:])):
        run("exec", "-i", container, "psql", "-U", "postgres", "-d", "hris", "-v", "ON_ERROR_STOP=1",
            input=migration.read_bytes(), stdout=subprocess.DEVNULL)
    binding = run("port", container, "5432", capture_output=True, text=True).stdout.strip()
    port = binding.rsplit(":", 1)[1]
    with tempfile.TemporaryDirectory(prefix="hris-jooq-") as temporary:
        config = Path(temporary) / "jooq.xml"
        config.write_text(f"""<?xml version="1.0" encoding="UTF-8"?>
<configuration xmlns="http://www.jooq.org/xsd/jooq-codegen-3.21.0.xsd">
<jdbc><driver>org.postgresql.Driver</driver><url>jdbc:postgresql://127.0.0.1:{port}/hris</url><user>postgres</user><password>codegen-only</password></jdbc>
<generator><database><name>org.jooq.meta.postgres.PostgresDatabase</name><inputSchema>public</inputSchema><excludes>flyway_schema_history</excludes></database>
<generate><deprecated>false</deprecated><records>true</records><pojos>false</pojos></generate>
<target><packageName>dev.fajar.hris.schema</packageName><directory>{escape(destination)}</directory><clean>true</clean></target></generator>
</configuration>""")
        subprocess.run(["java", "-Dorg.jooq.no-logo=true", "-Dorg.jooq.no-tips=true", "-cp", classpath,
                        "org.jooq.codegen.GenerationTool", str(config)], check=True)
finally:
    subprocess.run(docker + ["rm", "-f", container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
