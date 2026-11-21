# link-check

**Status:** implemented.

    python3 tools/link-check/cli.py .

Checks every relative Markdown link in the repository and, when a link points at
another Markdown file, that the anchor exists. External links are not fetched:
CI should not depend on the network. Binary files are skipped.

Slugs follow GitHub's rule: lowercase, punctuation removed, each whitespace
character becomes a hyphen (so "A — B" yields "a--b"). That means an anchor in
these docs can be checked the same way GitHub renders it.
