#!/bin/sh
# H1 follow-up: named volumes created before the non-root switch are root-owned, so the
# `app` user cannot write /uploads. Start as root, fix ownership, then drop privileges.
# /projects is a host bind mount; only fix it if the host side is already writable, so we
# never silently chown user files that belong to the host.
chown -R app:app /uploads
if [ -w /projects ]; then
  chown -R app:app /projects
fi
exec su-exec app "$@"
