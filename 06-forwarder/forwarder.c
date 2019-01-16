#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <sys/select.h>
#include <string.h>
#include <stdbool.h>
#include <time.h>
#include <signal.h>
#include <netdb.h>

#define BUFSIZE 4096

int listener;
int nfds = 0;
struct addrinfo *res_t;

typedef struct connection
{
	int client, server;
	struct connection *prev, *next;
	ssize_t cnt_cs, cnt_sc;
	char data_cs[BUFSIZE];
	char data_sc[BUFSIZE];
	time_t last_update;
} connection;

connection *head, *tail;

void check_socket_desc(int s)
{
    if (s >= FD_SETSIZE) 
    {
        fprintf(stderr, "Socket number out of range for select\n");
        exit(EXIT_FAILURE);
    }
    if (s + 1 > nfds) 
    	nfds = s + 1;
}

connection* add_connection(int client)
{
	connection *t;
    t = malloc(sizeof(connection));
    t->client = client;

    /*connection loop*/
    struct addrinfo *buf;
    for(buf = res_t; buf != NULL; buf = buf->ai_next) 
	{
    	t->server = socket(buf->ai_family, buf->ai_socktype, buf->ai_protocol);
		if(t->server == -1)
			continue;
		check_socket_desc(t->server);

		if (!connect(t->server, buf->ai_addr, buf->ai_addrlen))
	        break;
		close(t->server);
	}
	if(buf == NULL)
	{
		perror("connection to server");
	    close(t->client);
	    free(t);
	    return NULL;
	}
    printf("Connected to server\n");
    t->prev = NULL;
    t->next = head;
    if (head == NULL) 
        tail = t;
    else
        head->prev = t;
    head = t;
    t->cnt_sc = 0;
    t->cnt_cs = 0;
    t->last_update = time(&(t->last_update));

    return t;
}

void drop_connection(connection *t) 
{
    if (t == head && t == tail) 
    { 
        head = tail = NULL;
    }
    else if (t == head)
    {
        head = t->next;
        head->prev = NULL;
    } 
    else if (t == tail) 
    {
        tail = t->prev;
        tail->next = NULL;
    } 
    else 
    {
        t->prev->next = t->next;
        t->next->prev = t->prev;
    }
    printf("Connection dropped\n");
    close(t->client);
    close(t->server);
    free(t);
}



void set_select_mask(fd_set *readfds, fd_set *writefds)
{
	connection *t;
    t = head;
    time_t current;
 	FD_ZERO(readfds);
 	FD_ZERO(writefds);
    FD_SET(listener, readfds);

    while(t) 
    {
    	current = time(&current);

        if((t->cnt_cs < 0 && t->cnt_sc <= 0) || (t->cnt_sc < 0 && t->cnt_cs <= 0))
        {
           drop_connection(t);
        }
        else if (current > 0 && current - t->last_update > 5*60)
        {
        	drop_connection(t);
        }
        else 
        {
            if(t->cnt_cs == 0) 	
                FD_SET(t->client, readfds);
            if(t->cnt_sc == 0) 
                FD_SET(t->server, readfds);
            if(t->cnt_cs > 0) 
                FD_SET(t->server, writefds);
            if(t->cnt_sc > 0) 
                FD_SET(t->client, writefds);
        }
        t = t->next;
    }
}

void at_close()
{
	freeaddrinfo(res_t);
	close(listener);
}

int main(int argc, char*argv[])
{
	char *address;
	char *port_l, *port_t;
	const char *usage = "Usage: <port for listen> <address> <port for translate>\n";

	if(argc != 4)
	{
		fprintf(stderr, "%s", usage);
		exit(EXIT_FAILURE);
	}
	
	port_l = argv[1];
	port_t = argv[3];
	address = argv[2];

	int ret = 0;
	struct addrinfo hints, *res_l, *addr_buf;
	memset(&hints, 0, sizeof(hints));
	hints.ai_family = AF_INET;
	hints.ai_socktype = SOCK_STREAM;

	ret = getaddrinfo(address, port_t, &hints, &res_t);
	if (ret)
	{
		fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(ret));
		exit(EXIT_FAILURE);
	}

	hints.ai_flags = AI_PASSIVE;
	ret = getaddrinfo(NULL, port_l, &hints, &res_l);
	if (ret)
	{
		fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(ret));
		exit(EXIT_FAILURE);
	}

	/*bind loop*/
	for(addr_buf = res_l; addr_buf != NULL; addr_buf = addr_buf->ai_next) 
	{
		listener = socket(addr_buf->ai_family, addr_buf->ai_socktype, addr_buf->ai_protocol);
		if(listener == -1)
			continue;

		if(!bind(listener, addr_buf->ai_addr, addr_buf->ai_addrlen))
			break;
		close(listener);
	}
	if(addr_buf == NULL)
	{
		fprintf(stderr, "Couldn't bind");
		exit(EXIT_FAILURE);
	}
	freeaddrinfo(res_l);

	atexit(at_close);
	signal(SIGPIPE, SIG_IGN);
	signal(SIGINT, at_close);
	signal(SIGTERM, at_close);

	if(listen(listener, SOMAXCONN))		//since Linux 2.2 second arg specifies the queue length
	{									//for completly established sockets waiting to be accepted
		perror("listen");				//instead off the number of uncomplete requests
		exit(EXIT_FAILURE);				//SOMAXCONN == 128
	}
	check_socket_desc(listener);

	fd_set readfds, writefds;
	int ready, read;
	char buf[BUFSIZE];

	while(1)
	{
		set_select_mask(&readfds, &writefds);
		ready = select(nfds, &readfds, &writefds, NULL, NULL);

		if(ready < 0)
		{
			perror("select");
			break;
		}
		else if(ready == 0)
			continue;

		/*Some descriptors are ready*/
		connection *t = head;
    
	    while(t) 
	    {
	    	/*recv from client*/
	        if(t->cnt_cs == 0 && FD_ISSET(t->client, &readfds)) 
	        {
	            t->cnt_cs = recv(t->client, t->data_cs, sizeof(t->data_cs), 0);
    			t->last_update = time(&(t->last_update));
	            if(t->cnt_cs == 0)
	            	t->cnt_cs = -1;
	        }

	        /*recv from server*/
	        if(t->cnt_sc == 0 && FD_ISSET(t->server, &readfds)) 
	        {
	            t->cnt_sc = recv(t->server, t->data_sc, sizeof(t->data_sc), 0);
    			t->last_update = time(&(t->last_update));
	            if(t->cnt_sc == 0)
	            	t->cnt_sc = -1;
	        }

	        /*send to server*/
	        if(t->cnt_cs > 0 && FD_ISSET(t->server, &writefds)) 
	        {
	            int res = send(t->server, t->data_cs, t->cnt_cs, 0);
    			t->last_update = time(&(t->last_update));
	            if (res == -1) 
	            	t->cnt_sc = -1;
	            else t->cnt_cs = 0; 
	        }

	        /*send to client*/
	        if(t->cnt_sc > 0 && FD_ISSET(t->client, &writefds)) 
	        {
	            int res = send(t->client, t->data_sc, t->cnt_sc, 0);
    			t->last_update = time(&(t->last_update));
	            if (res == -1) 
	            	t->cnt_cs = -1;
	            else t->cnt_sc = 0;
	        }
	            
	        t = t->next;
	    }

	    /*new client*/
		if(FD_ISSET(listener, &readfds))
		{
			int client = accept(listener, NULL, NULL);	
			if(client < 0)
			{
				perror("accept");
				break;
			}
			printf("New client!\n");
			check_socket_desc(client);
			if(add_connection(client) == NULL)
			{
				perror("add connection");
				break;
			}
		}
	}

	exit(EXIT_SUCCESS);
}