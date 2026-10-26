# FTP evidence review
Query current user_profile and the relevant ride's historical FTP/weight separately. Retrieve ride_metrics for continuous best efforts and coverage.
Short best efforts describe recorded performance, not maximum capacity unless a maximal test was established. A best 20-minute segment times 0.95 is not automatically a validated FTP.
The app's ramp estimates use measured ramp-only best continuous 60s times 0.75; SQL records do not store test-stage metadata, so do NOT reconstruct a completed ramp test from an arbitrary best minute.
Compare like-for-like durations and historical FTP snapshots. HR is supporting context, not proof of FTP. Do not estimate precise FTP from easy riding, sparse samples or an interrupted effort.
Report whether evidence suggests retaining FTP, collecting a better comparable effort or performing the app's explicit FTP test. State uncertainty; never auto-update settings.
