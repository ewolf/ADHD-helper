/**
 * Round Tooit Gmail helper
 * Fetches all emails from known senders (people user has emailed)
 */
async function _rtLoadEmails(token) {
    var hdrs = { Authorization: 'Bearer ' + token };
    var emailRegex = /[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}/g;

    // Build known senders from Sent folder
    var sentResp = await fetch(
        'https://www.googleapis.com/gmail/v1/users/me/messages?labelIds=SENT&maxResults=200',
        { headers: hdrs }
    );
    var sentData = await sentResp.json();
    var senders = {};

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

    // Build search query for all known senders
    var senderList = Object.keys(senders);
    if (senderList.length === 0) return [];

    // Query inbox for emails from known senders (all, not just unread)
    // Gmail search: from:(addr1 OR addr2 OR ...)
    // Batch in groups to avoid query length limits
    var allFiltered = [];
    var batchSize = 20;
    for (var b = 0; b < senderList.length; b += batchSize) {
        var batch = senderList.slice(b, b + batchSize);
        var fromQuery = 'in:inbox from:(' + batch.join(' OR ') + ')';
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
                    var dateH = '';
                    for (var h = 0; h < imHeaders.length; h++) {
                        if (imHeaders[h].name === 'From') fromH = imHeaders[h].value;
                        if (imHeaders[h].name === 'Subject') subjectH = imHeaders[h].value;
                        if (imHeaders[h].name === 'Date') dateH = imHeaders[h].value;
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

    // Sort by date descending
    allFiltered.sort(function(a, b) { return b.receivedAt - a.receivedAt; });

    // Ensure ADHD label and apply to these messages
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
            for (var m = 0; m < allFiltered.length; m++) {
                try {
                    await fetch(
                        'https://www.googleapis.com/gmail/v1/users/me/messages/' + allFiltered[m].id + '/modify',
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

    return allFiltered;
}
