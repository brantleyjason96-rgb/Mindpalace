# For any coding agent

1. Read [`CLAUDE.md`](CLAUDE.md). Its rules are binding (permissions need the owner's sign-off;
   all canonical writes go through the command handler; original note text is never rewritten;
   every schema change needs a migration and a test).
2. Read [`docs/plans/INDEX.md`](docs/plans/INDEX.md) for the work queue, its order, and the
   prompt template for executing one step.
3. Before merging anything, fill in [`docs/plans/REVIEW_GATES.md`](docs/plans/REVIEW_GATES.md).

The app cannot be built in a cloud sandbox without the Android SDK. Use the "Android build"
GitHub Actions workflow as the test loop.
