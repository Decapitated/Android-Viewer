var http = require('http').createServer();
var io = require('socket.io')(http);


//Runs when client connects
io.on('connection', function(socket) {
    console.log('A user connected');
    
    socket.on('disconnect', function() {
        console.log('user disconnected');
    });
    
    socket.on('message', function(from, msg){
        socket.broadcast.emit('message', from, msg);
    });
    
    socket.on('frame', function(frame){
        socket.broadcast.emit('frame', frame);
    });
    
});

console.log('Waiting for connections...');
io.listen(5555);
