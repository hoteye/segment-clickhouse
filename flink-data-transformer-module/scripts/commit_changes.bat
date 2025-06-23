@echo off
cd /d d:\java-demo\segment-alarm-clickhouse

git add .
git commit -m "Update settings and resolve Java Language Server issue"
git push

echo Changes have been committed and pushed.
pause
