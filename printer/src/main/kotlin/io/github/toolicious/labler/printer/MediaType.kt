package io.github.toolicious.labler.printer

/**
 * Paper type: die-cut labels with a gap or continuous tape.
 *
 * Persisted by name in templates, print history and backups, so the constants keep their
 * spelling. Which of them a printer actually understands is [PrinterProtocol.supportedMedia].
 */
enum class MediaType { DIE_CUT, CONTINUOUS }
