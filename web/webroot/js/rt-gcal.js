/**
 * Round Tooit Google Calendar helper
 * Queries all visible calendars (including shared) with proper timezone handling
 */

function _rtLocalRFC3339(date) {
    // Format a Date as RFC3339 in local timezone (not UTC)
    var pad = function(n) { return n < 10 ? '0' + n : '' + n; };
    var offset = -date.getTimezoneOffset();
    var sign = offset >= 0 ? '+' : '-';
    var absOff = Math.abs(offset);
    var offH = pad(Math.floor(absOff / 60));
    var offM = pad(absOff % 60);
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) +
        'T' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds()) +
        sign + offH + ':' + offM;
}

async function _rtGetCalendarIds(token) {
    var hdrs = { Authorization: 'Bearer ' + token };
    var resp = await fetch(
        'https://www.googleapis.com/calendar/v3/users/me/calendarList',
        { headers: hdrs }
    );
    var data = await resp.json();
    var ids = [];
    var items = data.items || [];
    for (var i = 0; i < items.length; i++) {
        // Include calendars the user owns or has read access to
        var role = items[i].accessRole;
        if (role === 'owner' || role === 'writer' || role === 'reader') {
            ids.push(items[i].id);
        }
    }
    return ids;
}

async function _rtFetchEventsFromCalendar(token, calendarId, timeMin, timeMax, maxResults) {
    var hdrs = { Authorization: 'Bearer ' + token };
    var url = 'https://www.googleapis.com/calendar/v3/calendars/' +
        encodeURIComponent(calendarId) +
        '/events?singleEvents=true&orderBy=startTime' +
        '&timeMin=' + encodeURIComponent(timeMin);
    if (timeMax) url += '&timeMax=' + encodeURIComponent(timeMax);
    if (maxResults) url += '&maxResults=' + maxResults;

    var resp = await fetch(url, { headers: hdrs });
    var data = await resp.json();
    return (data.items || []).map(function(e) {
        return {
            id: e.id,
            calendarId: calendarId,
            title: e.summary || '(No title)',
            description: e.description || '',
            location: e.location || '',
            startTime: new Date(e.start.dateTime || e.start.date).getTime(),
            endTime: new Date(e.end.dateTime || e.end.date).getTime()
        };
    });
}

async function _rtDoGoogleLoad(token) {
    console.log('[RT] _rtDoGoogleLoad with token');

    store.set('calLoading', true);
    store.set('emailLoading', true);

    // Run calendar and email loads in parallel
    var calendarPromise = _rtLoadCalendarData(token);
    var emailPromise = _rtLoadEmailData(token);

    calendarPromise.then(function() {
        store.set('calLoading', false);
    });
    emailPromise.then(function() {
        store.set('emailLoading', false);
    });

    await Promise.all([calendarPromise, emailPromise]);
}

async function _rtLoadCalendarData(token) {
    try {
        var today = await _rtLoadTodayEvents(token);
        console.log('[RT] today events:', today.length);
        store.set('calTodayEvents', today);
    } catch (err) {
        console.error('[RT] calendar error:', err);
    }
    try {
        var upcoming = await _rtLoadUpcomingEvents(token);
        console.log('[RT] upcoming events:', upcoming.length);
        store.set('calUpcomingEvents', upcoming);
    } catch (err) {
        console.error('[RT] upcoming error:', err);
    }
}

async function _rtLoadEmailData(token) {
    try {
        var emails = await _rtLoadEmails(token);
        console.log('[RT] emails:', emails.length);

        var doneIds = {};
        var app = store.get('app');
        if (app) {
            try {
                var doneResult = await app.getDoneEmails();
                var ids = doneResult.gmail_ids || [];
                for (var i = 0; i < ids.length; i++) {
                    doneIds[ids[i]] = true;
                }
                console.log('[RT] done emails from server:', ids.length);
            } catch (e) {
                console.error('[RT] getDoneEmails error:', e);
            }
        }
        store.set('doneEmailIds', doneIds);

        var filtered = emails.filter(function(em) { return !doneIds[em.id]; });
        console.log('[RT] emails after filtering done:', filtered.length);
        store.set('emailList', filtered);
    } catch (err) {
        console.error('[RT] email error:', err);
    }
}

async function _rtLoadTodayEvents(token) {
    var calIds = await _rtGetCalendarIds(token);
    var now = new Date();
    var startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    var endOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
    var timeMin = _rtLocalRFC3339(startOfDay);
    var timeMax = _rtLocalRFC3339(endOfDay);

    var allEvents = [];
    for (var i = 0; i < calIds.length; i++) {
        try {
            var events = await _rtFetchEventsFromCalendar(token, calIds[i], timeMin, timeMax, 50);
            allEvents = allEvents.concat(events);
        } catch (e) {}
    }

    // Dedupe, filter out ended events, sort by start time
    var now = Date.now();
    var seen = {};
    var unique = [];
    for (var j = 0; j < allEvents.length; j++) {
        if (!seen[allEvents[j].id] && allEvents[j].endTime > now) {
            seen[allEvents[j].id] = true;
            unique.push(allEvents[j]);
        }
    }
    unique.sort(function(a, b) { return a.startTime - b.startTime; });
    return unique;
}

function _rtTimePeriod(timestamp) {
    var hour = new Date(timestamp).getHours();
    if (hour < 12) return 'morning';
    if (hour < 17) return 'afternoon';
    return 'evening';
}

/**
 * Returns an array of day groups, each with:
 *   { label: "Tomorrow", morning: [...], afternoon: [...], evening: [...] }
 * Up to 3 days with events.
 */
async function _rtLoadUpcomingEvents(token) {
    var calIds = await _rtGetCalendarIds(token);
    var now = new Date();
    var tomorrowStart = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
    var timeMin = _rtLocalRFC3339(tomorrowStart);
    var today = new Date(now.getFullYear(), now.getMonth(), now.getDate());

    var allEvents = [];
    for (var i = 0; i < calIds.length; i++) {
        try {
            var events = await _rtFetchEventsFromCalendar(token, calIds[i], timeMin, null, 30);
            allEvents = allEvents.concat(events);
        } catch (e) {}
    }

    // Dedupe and sort
    var seen = {};
    var unique = [];
    for (var j = 0; j < allEvents.length; j++) {
        if (!seen[allEvents[j].id]) {
            seen[allEvents[j].id] = true;
            unique.push(allEvents[j]);
        }
    }
    unique.sort(function(a, b) { return a.startTime - b.startTime; });

    // Group by day — collect up to 3 distinct days
    var dayGroups = [];
    var dayMap = {};
    for (var k = 0; k < unique.length; k++) {
        var ev = unique[k];
        var evDate = new Date(ev.startTime);
        var dayKey = evDate.getFullYear() + '-' + evDate.getMonth() + '-' + evDate.getDate();

        if (!dayMap[dayKey]) {
            if (dayGroups.length >= 3) break;
            var eventDay = new Date(evDate.getFullYear(), evDate.getMonth(), evDate.getDate());
            var diff = Math.round((eventDay - today) / (1000 * 60 * 60 * 24));
            var label;
            if (diff === 1) {
                label = 'Tomorrow';
            } else if (diff < 7) {
                label = 'In ' + diff + ' days';
            } else {
                label = evDate.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
            }
            dayMap[dayKey] = { label: label, morning: [], afternoon: [], evening: [] };
            dayGroups.push(dayMap[dayKey]);
        }

        var period = _rtTimePeriod(ev.startTime);
        dayMap[dayKey][period].push(ev.title);
    }

    return dayGroups;
}
