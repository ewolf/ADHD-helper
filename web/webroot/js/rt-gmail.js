/**
 * Round Tooit Gmail helper
 * Fetches all emails from known senders (people user has emailed)
 * Caches results in localStorage; only fetches new emails on subsequent loads
 */

// Cache keys
var _RT_EMAIL_CACHE_KEY = 'rt_email_cache';
var _RT_EMAIL_LAST_CHECK_KEY = 'rt_email_last_check';
var _RT_EMAIL_SENDERS_KEY = 'rt_email_known_senders';

function _rtGetEmailCache() {
    try {
        var raw = localStorage.getItem(_RT_EMAIL_CACHE_KEY);
        return raw ? JSON.parse(raw) : [];
    } catch (e) { return []; }
}

function _rtSetEmailCache(emails) {
    try {
        localStorage.setItem(_RT_EMAIL_CACHE_KEY, JSON.stringify(emails));
    } catch (e) {}
}

function _rtGetLastEmailCheck() {
    var ts = localStorage.getItem(_RT_EMAIL_LAST_CHECK_KEY);
    return ts ? parseInt(ts) : 0;
}

function _rtSetLastEmailCheck(ts) {
    localStorage.setItem(_RT_EMAIL_LAST_CHECK_KEY, '' + ts);
}

function _rtGetCachedSenders() {
    try {
        var raw = localStorage.getItem(_RT_EMAIL_SENDERS_KEY);
        return raw ? JSON.parse(raw) : null;
    } catch (e) { return null; }
}

function _rtSetCachedSenders(senders) {
    try {
        localStorage.setItem(_RT_EMAIL_SENDERS_KEY, JSON.stringify(senders));
    } catch (e) {}
}

async function _rtBuildKnownSenders(token) {
    var hdrs = { Authorization: 'Bearer ' + token };
    var emailRegex = /[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}/g;
    var senders = {};

    var sentResp = await fetch(
        'https://www.googleapis.com/gmail/v1/users/me/messages?labelIds=SENT&maxResults=200',
        { headers: hdrs }
    );
    var sentData = await sentResp.json();

    var msgIds = (sentData.messages || []).slice(0, 100);
    for (var i = 0; i < msgIds.length; i++) {
        try {
            var mResp = await fetch(
                'https://www.googleapis.com/gmail/v1/users/me/messages/' + msgIds[i].id + '?format=metadata&metadataHeaders=To&metadataHeaders=Cc',
                { headers: hdrs }
            );
            var mData = await mResp.json();
            var headers = (mData.payload && mData.payload.headers) || [];
            for (var j = 0; j < headers.length; j++) {
                if (headers[j].name === 'To' || headers[j].name === 'Cc') {
                    var addrs = headers[j].value.match(emailRegex);
                    if (addrs) addrs.forEach(function(a) { senders[a.toLowerCase()] = true; });
                }
            }
        } catch (e) {}
    }
    return senders;
}

async function _rtFetchEmailsSince(token, senders, sinceTimestamp) {
    var hdrs = { Authorization: 'Bearer ' + token };
    var senderList = Object.keys(senders);
    if (senderList.length === 0) return [];

    var allFiltered = [];
    var batchSize = 20;

    // Gmail "after:" uses epoch seconds
    var afterQuery = sinceTimestamp > 0 ? ' after:' + Math.floor(sinceTimestamp / 1000) : '';

    for (var b = 0; b < senderList.length; b += batchSize) {
        var batch = senderList.slice(b, b + batchSize);
        var fromQuery = 'in:inbox from:(' + batch.join(' OR ') + ')' + afterQuery;
        try {
            var inboxResp = await fetch(
                'https://www.googleapis.com/gmail/v1/users/me/messages?q=' + encodeURIComponent(fromQuery) + '&maxResults=50',
                { headers: hdrs }
            );
            var inboxData = await inboxResp.json();

            var inboxMsgs = (inboxData.messages || []);
            for (var k = 0; k < inboxMsgs.length; k++) {
                try {
                    var imResp = await fetch(
                        'https://www.googleapis.com/gmail/v1/users/me/messages/' + inboxMsgs[k].id + '?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date',
                        { headers: hdrs }
                    );
                    var imData = await imResp.json();
                    var imHeaders = (imData.payload && imData.payload.headers) || [];
                    var fromH = '';
                    var subjectH = '(No subject)';
                    for (var h = 0; h < imHeaders.length; h++) {
                        if (imHeaders[h].name === 'From') fromH = imHeaders[h].value;
                        if (imHeaders[h].name === 'Subject') subjectH = imHeaders[h].value;
                    }
                    var fromMatch = fromH.match(/[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}/);
                    var fromEmail = fromMatch ? fromMatch[0].toLowerCase() : '';

                    if (!senders[fromEmail]) continue;

                    var nameIdx = fromH.indexOf(' <');
                    var senderName = nameIdx > 0 ? fromH.substring(0, nameIdx).replace(/"/g, '').trim() : null;

                    allFiltered.push({
                        id: inboxMsgs[k].id,
                        senderEmail: fromEmail,
                        senderName: senderName,
                        subject: subjectH,
                        snippet: imData.snippet || '',
                        receivedAt: parseInt(imData.internalDate || '0'),
                        isUnread: (imData.labelIds || []).indexOf('UNREAD') >= 0
                    });
                } catch (e) {}
            }
        } catch (e) {}
    }

    return allFiltered;
}

async function _rtLoadEmails(token) {
    var lastCheck = _rtGetLastEmailCheck();
    var cachedEmails = _rtGetEmailCache();
    var now = Date.now();

    console.log('[RT-EMAIL] last check:', lastCheck ? new Date(lastCheck).toLocaleString() : 'never', 'cached:', cachedEmails.length);

    // Use cached senders if available, otherwise build fresh
    var senders = _rtGetCachedSenders();
    if (!senders) {
        console.log('[RT-EMAIL] building known senders list');
        senders = await _rtBuildKnownSenders(token);
        _rtSetCachedSenders(senders);
    }

    // Fetch only new emails since last check
    var newEmails = await _rtFetchEmailsSince(token, senders, lastCheck);
    console.log('[RT-EMAIL] new emails since last check:', newEmails.length);

    // Merge: add new emails, dedupe by ID
    var emailMap = {};
    for (var i = 0; i < cachedEmails.length; i++) {
        emailMap[cachedEmails[i].id] = cachedEmails[i];
    }
    for (var j = 0; j < newEmails.length; j++) {
        emailMap[newEmails[j].id] = newEmails[j];
    }

    // Convert back to sorted array
    var merged = [];
    for (var id in emailMap) {
        merged.push(emailMap[id]);
    }
    merged.sort(function(a, b) { return b.receivedAt - a.receivedAt; });

    // Update cache and timestamp
    _rtSetEmailCache(merged);
    _rtSetLastEmailCheck(now);

    // Ensure ADHD label for new emails only
    if (newEmails.length > 0) {
        _rtApplyAdhdLabel(token, newEmails);
    }

    return merged;
}

async function _rtApplyAdhdLabel(token, emails) {
    var hdrs = { Authorization: 'Bearer ' + token };
    try {
        var labelId = null;
        var labelsResp = await fetch('https://www.googleapis.com/gmail/v1/users/me/labels', { headers: hdrs });
        var labelsData = await labelsResp.json();
        var existing = (labelsData.labels || []).filter(function(l) { return l.name === 'ADHD'; });
        if (existing.length > 0) {
            labelId = existing[0].id;
        } else {
            var createResp = await fetch('https://www.googleapis.com/gmail/v1/users/me/labels', {
                method: 'POST',
                headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: 'ADHD', labelListVisibility: 'labelShow', messageListVisibility: 'show' })
            });
            var created = await createResp.json();
            labelId = created.id;
        }
        if (labelId) {
            for (var m = 0; m < emails.length; m++) {
                try {
                    await fetch(
                        'https://www.googleapis.com/gmail/v1/users/me/messages/' + emails[m].id + '/modify',
                        {
                            method: 'POST',
                            headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
                            body: JSON.stringify({ addLabelIds: [labelId] })
                        }
                    );
                } catch (e) {}
            }
        }
    } catch (e) {}
}
