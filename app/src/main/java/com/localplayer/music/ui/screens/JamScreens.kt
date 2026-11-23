package com.localplayer.music.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.data.library.SongSort
import com.localplayer.music.data.library.TextNormalizer
import com.localplayer.music.jam.JAM_GATE_MAX
import com.localplayer.music.jam.JAM_GATE_MIN
import com.localplayer.music.jam.JamAction
import com.localplayer.music.jam.JamHost
import com.localplayer.music.jam.JamInvite
import com.localplayer.music.jam.JamManager
import com.localplayer.music.jam.JamManager.GuestStatus
import com.localplayer.music.jam.JamManager.Role
import com.localplayer.music.jam.JamMember
import com.localplayer.music.jam.JamNames
import com.localplayer.music.jam.QrCode
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.FilledField
import com.localplayer.music.ui.components.LocalBottomBarInset
import com.localplayer.music.ui.components.SettingsChipRow
import com.localplayer.music.ui.components.SettingsCustomRow
import com.localplayer.music.ui.components.SettingsGroup
import com.localplayer.music.ui.components.SettingsNote
import com.localplayer.music.ui.components.SettingsScaffold
import com.localplayer.music.ui.components.SettingsTopBar
import com.localplayer.music.ui.components.SettingsSwitchRow
import com.localplayer.music.ui.nav.NavState

/**
 * Settings > Jam: listen together over the local network. Off until switched on. One phone hosts (its queue is the
 * shared queue and it plays), the others join with the QR code, give their name and wait for the host to accept them,
 * then they see the queue and add songs from their own phone.
 */
@Composable
fun JamScreen(nav: NavState, container: AppContainer) {
    val jam = container.jam
    val settings = container.settings
    val enabled by settings.jamEnabled.collectAsStateWithLifecycle()
    val ui by jam.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }

    val sent = stringResource(R.string.jam_toast_sent)
    val failed = stringResource(R.string.jam_toast_failed)
    val tooBig = stringResource(R.string.jam_toast_too_big)
    val unreadable = stringResource(R.string.jam_toast_unreadable)
    val noSpace = stringResource(R.string.jam_toast_no_space)
    LaunchedEffect(jam, sent, failed, tooBig, unreadable, noSpace) {
        jam.events.collect { event ->
            val text = when (event) {
                JamManager.Event.SONG_SENT -> sent
                JamManager.Event.SONG_FAILED -> failed
                JamManager.Event.SONG_TOO_BIG -> tooBig
                JamManager.Event.SONG_UNREADABLE -> unreadable
                JamManager.Event.HOST_NO_SPACE -> noSpace
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    if (picking && ui.role == Role.GUEST) {
        BackHandler { picking = false }
        JamSongPicker(jam, container, ui.uploading) { picking = false }
    } else {
        SettingsScaffold(title = stringResource(R.string.jam_title), onBack = { nav.pop() }) {
            SettingsGroup(note = stringResource(R.string.jam_switch_note)) {
                item {
                    SettingsSwitchRow(
                        stringResource(R.string.jam_switch),
                        enabled,
                        { on ->
                            if (!on) jam.leave()
                            settings.setJamEnabled(on)
                        },
                    )
                }
            }
            if (enabled) {
                when (ui.role) {
                    Role.NONE -> JamIdle(jam, ui)
                    Role.HOST -> JamHostContent(jam, ui)
                    Role.GUEST -> JamGuestContent(jam, ui) { picking = true }
                }
            }
        }
    }
}

@Composable
private fun JamIdle(jam: JamManager, ui: JamManager.Ui) {
    val context = LocalContext.current
    var hostName by rememberSaveable { mutableStateOf(jam.lastName()) }
    var address by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var invite by remember { mutableStateOf<JamInvite?>(null) }
    val scannerMissing = stringResource(R.string.jam_scan_failed)
    val notAJamCode = stringResource(R.string.jam_bad_invite)
    val badAddress = stringResource(R.string.jam_invalid_address)

    SettingsGroup(
        title = stringResource(R.string.jam_start_group),
        note = when (ui.hostProblem) {
            JamManager.HostProblem.NO_NETWORK -> stringResource(R.string.jam_no_network)
            JamManager.HostProblem.PORT_BUSY -> stringResource(R.string.jam_port_busy)
            null -> null
        },
    ) {
        item {
            SettingsCustomRow {
                FilledField(hostName, { hostName = it }, stringResource(R.string.jam_your_name))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { jam.startHosting(hostName) },
                    enabled = JamNames.clean(hostName) != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.jam_start)) }
            }
        }
    }

    JamGateGroup(jam)

    SettingsGroup(title = stringResource(R.string.jam_join_group), note = stringResource(R.string.jam_or_type)) {
        item {
            SettingsCustomRow {
                Button(
                    onClick = {
                        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                        GmsBarcodeScanning.getClient(context, options).startScan()
                            .addOnSuccessListener { barcode ->
                                val scanned = JamInvite.parse(barcode.rawValue)
                                if (scanned != null) {
                                    invite = scanned
                                } else {
                                    Toast.makeText(context, notAJamCode, Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, scannerMissing, Toast.LENGTH_LONG).show()
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.jam_scan)) }
            }
        }
        item {
            SettingsCustomRow {
                FilledField(address, { address = it }, stringResource(R.string.jam_address))
                Spacer(Modifier.height(8.dp))
                FilledField(code, { code = it.filter { c -> c.isDigit() }.take(6) }, stringResource(R.string.jam_code))
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val typed = JamInvite.fromParts(address, code)
                        if (typed != null) invite = typed else Toast.makeText(context, badAddress, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.jam_join_button)) }
            }
        }
    }

    invite?.let { target ->
        JamNameDialog(
            initial = jam.lastName(),
            onSubmit = { name ->
                invite = null
                jam.join(target, name)
            },
            onDismiss = { invite = null },
        )
    }
}

/** The window a guest sees after scanning: the name goes in, "Submit" sends the request to the host. */
@Composable
private fun JamNameDialog(initial: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.jam_name_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.jam_name_dialog_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                FilledField(name, { name = it }, stringResource(R.string.jam_your_name))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(name) }, enabled = JamNames.clean(name) != null) {
                Text(stringResource(R.string.jam_submit))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The host chooses how many songs every guest loads before the music starts. */
@Composable
private fun JamGateGroup(jam: JamManager) {
    val size by jam.gateSizeState.collectAsStateWithLifecycle()
    SettingsGroup(title = stringResource(R.string.jam_gate_title), note = stringResource(R.string.jam_gate_note)) {
        item {
            SettingsCustomRow {
                SettingsChipRow(
                    labels = (JAM_GATE_MIN..JAM_GATE_MAX).map { it.toString() },
                    selected = size - JAM_GATE_MIN,
                    onSelect = { jam.setGateSize(it + JAM_GATE_MIN) },
                )
            }
        }
    }
}

@Composable
private fun JamHostContent(jam: JamManager, ui: JamManager.Ui) {
    val invite = ui.invite
    if (invite != null) {
        val bitmap = remember(invite) { QrCode.bitmap(invite.toUri(), 720) }
        SettingsGroup(title = stringResource(R.string.jam_hosting_title), note = stringResource(R.string.jam_scan_hint)) {
            item {
                SettingsCustomRow {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(240.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.jam_address_line, invite.address()), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.jam_code_line, invite.code),
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (ui.pending.isNotEmpty()) {
        SettingsGroup(title = stringResource(R.string.jam_requests)) {
            ui.pending.forEach { request: JamHost.Request ->
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            request.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { jam.deny(request.id) }) { Text(stringResource(R.string.jam_refuse)) }
                        Button(onClick = { jam.approve(request.id) }) { Text(stringResource(R.string.jam_accept)) }
                    }
                }
            }
        }
    }

    if (ui.waiting) {
        SettingsGroup(title = stringResource(R.string.jam_waiting_title), note = stringResource(R.string.jam_waiting_note)) {
            item {
                SettingsCustomRow {
                    Text(
                        stringResource(R.string.jam_ready_count, ui.readyCount, ui.guestCount),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { jam.startNow() }, enabled = ui.enough, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.jam_start_now))
                    }
                }
            }
        }
    }

    JamVoteGroup(jam, ui, canAsk = false)

    JamGateGroup(jam)

    SettingsGroup(title = stringResource(R.string.jam_members, ui.members.size), note = stringResource(R.string.jam_host_note)) {
        if (ui.members.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.jam_no_members),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        ui.members.forEach { member: JamMember ->
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        member.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    if (ui.waiting) {
                        Text(
                            stringResource(if (member.id in ui.readyMembers) R.string.jam_member_ready else R.string.jam_member_loading),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { jam.kick(member.id) }) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.jam_remove))
                    }
                }
            }
        }
    }

    Button(onClick = { jam.leave() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.jam_end)) }
}

/** The vote for the next / previous song: the buttons to ask (guests) and the open request with Yes / No. */
@Composable
private fun JamVoteGroup(jam: JamManager, ui: JamManager.Ui, canAsk: Boolean) {
    val proposal = ui.proposal
    val result = ui.voteResult
    if (proposal == null && result == null && !canAsk) return
    SettingsGroup {
        if (proposal != null) {
            item {
                SettingsCustomRow {
                    Text(
                        stringResource(
                            if (proposal.action == JamAction.NEXT) R.string.jam_vote_title_next else R.string.jam_vote_title_prev,
                            proposal.byName,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.jam_vote_counts, proposal.yes.size, proposal.no.size, proposal.needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val me = ui.myId
                    if (me != null && me != proposal.byId && me !in proposal.yes && me !in proposal.no) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { jam.vote(proposal.id, false) }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.jam_vote_no))
                            }
                            Button(onClick = { jam.vote(proposal.id, true) }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.jam_vote_yes))
                            }
                        }
                    } else if (me != null && me != proposal.byId) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.jam_vote_you_voted), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (result != null) {
            item {
                SettingsCustomRow {
                    val text = when {
                        result.action == JamAction.NEXT && result.passed -> R.string.jam_vote_passed_next
                        result.action == JamAction.NEXT -> R.string.jam_vote_failed_next
                        result.passed -> R.string.jam_vote_passed_prev
                        else -> R.string.jam_vote_failed_prev
                    }
                    Text(stringResource(text, result.byName), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (canAsk && proposal == null) {
            item {
                SettingsCustomRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { jam.requestChange(JamAction.PREVIOUS) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.jam_ask_prev))
                        }
                        OutlinedButton(onClick = { jam.requestChange(JamAction.NEXT) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.jam_ask_next))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JamGuestContent(jam: JamManager, ui: JamManager.Ui, onPick: () -> Unit) {
    val message = when (ui.guestStatus) {
        GuestStatus.CONNECTING -> R.string.jam_status_connecting
        GuestStatus.WAITING -> R.string.jam_status_waiting
        GuestStatus.DENIED -> R.string.jam_status_denied
        GuestStatus.NO_ANSWER -> R.string.jam_status_no_answer
        GuestStatus.WRONG_CODE -> R.string.jam_status_wrong_code
        GuestStatus.FULL -> R.string.jam_status_full
        GuestStatus.UNREACHABLE -> R.string.jam_status_unreachable
        GuestStatus.REMOVED -> R.string.jam_status_removed
        GuestStatus.CONNECTED -> null
    }
    val state = ui.state
    if (message != null || state == null) {
        val waiting = ui.guestStatus == GuestStatus.CONNECTING || ui.guestStatus == GuestStatus.WAITING
        SettingsGroup {
            item {
                SettingsCustomRow {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (waiting) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.size(16.dp))
                        }
                        Text(
                            stringResource(message ?: R.string.jam_status_connecting),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        OutlinedButton(onClick = { jam.leave() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (waiting) R.string.jam_leave else R.string.jam_back))
        }
        return
    }

    JamVoteGroup(jam, ui, canAsk = true)

    SettingsGroup(title = stringResource(R.string.jam_connected_to, state.hostName)) {
        item {
            SettingsCustomRow {
                val current = state.queue.getOrNull(state.currentIndex)
                Text(
                    stringResource(R.string.jam_now_playing),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    current?.title ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current != null && current.id !in ui.loaded) {
                    Spacer(Modifier.height(8.dp))
                    val progress = ui.loadProgress
                    Text(
                        stringResource(R.string.jam_loading_song),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (current != null) {
                    Text(
                        current.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item {
            SettingsSwitchRow(
                stringResource(R.string.jam_play_here),
                ui.playLocally,
                { jam.setPlayLocally(it) },
                note = stringResource(R.string.jam_play_here_note),
            )
        }
        item {
            SettingsCustomRow {
                Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.jam_add_song))
                }
                if (ui.uploading > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.jam_sending, ui.uploading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    SettingsGroup(title = stringResource(R.string.jam_queue)) {
        if (state.queue.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.jam_queue_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        state.queue.forEachIndexed { index, track ->
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (index == state.currentIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == state.currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(track.artist, stringResource(R.string.jam_added_by, track.addedBy)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    SettingsNote(state.members.joinToString(", ") { it.name }.ifEmpty { state.hostName })
    OutlinedButton(onClick = { jam.leave() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.jam_leave)) }
}

/** The guest's own library: tap a song to send it to the host. */
@Composable
private fun JamSongPicker(jam: JamManager, container: AppContainer, uploading: Int, onBack: () -> Unit) {
    val songs by remember { container.library.songs(SongSort.TITLE) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(setOf<Long>()) }
    val shown = remember(songs, query) {
        val needle = TextNormalizer.fold(query)
        if (needle.isEmpty()) {
            songs
        } else {
            songs.filter { TextNormalizer.fold(it.title).contains(needle) || TextNormalizer.fold(it.artist).contains(needle) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(R.string.jam_pick_title), onBack)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledField(query, { query = it }, stringResource(R.string.jam_search))
            if (uploading > 0) {
                Text(
                    stringResource(R.string.jam_sending, uploading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(shown, key = { it.id }) { song ->
                val done = song.id in sent
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !done) {
                            sent = sent + song.id
                            jam.addSong(song)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            song.artist.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (done) {
                        Text(stringResource(R.string.jam_sent), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(AppIcons.Add, contentDescription = stringResource(R.string.jam_add_song))
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp + LocalBottomBarInset.current)) }
        }
    }
}
