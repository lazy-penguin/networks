#include <sys/poll.h>
#include <signal.h>
#include "socks5.h"

#define BUFSIZE 4096
#define FD_OPEN 200

typedef struct Attr
{
	char buf[BUFSIZE];
	int offset;
	int end;
	int size;
	bool eof;
} Attr;

int listener, events_cnt, not_establish_cnt;
struct pollfd *events, *establish_events, *incoming_events, *outcoming_events;
Attr inAttrs[FD_OPEN], outAttrs[FD_OPEN];

bool isEnd = false;

void at_close()
{
	close(listener);
	free(events);
}

void signal_end(int signal)
{
	isEnd = true;
}

void close_connection(struct pollfd *connection, int i)
{
	close(connection[i].fd);
	connection[i].fd = -1;
	connection[i].events = 0;
}

void init_Attr(Attr *attrs, int i)
{
	attrs[i].offset = 0;
	attrs[i].end = 0;
	attrs[i].size = BUFSIZE;
	memset(attrs[i].buf, 0, attrs[i].size);
	attrs[i].eof = false;
}

int find_first_free_index()
{
	for(int i = 0; i < FD_OPEN; i++)
	{
		if(establish_events[i].fd == -1)
			return i;
	}
	return -1;
}

int bind_socket(char *port)
{
	int listener;
	struct addrinfo hints, *addr_res, *addr_buf;
	memset(&hints, 0, sizeof(hints));
	hints.ai_family = AF_INET;
	hints.ai_socktype = SOCK_STREAM;
	hints.ai_flags = AI_PASSIVE;

	int ret = getaddrinfo(NULL, port, &hints, &addr_res);
	if (ret)
	{
		fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(ret));
		return 0;
	}
	/*bind loop*/
	int optval = 1;
	for(addr_buf = addr_res; addr_buf != NULL; addr_buf = addr_buf->ai_next) 
	{
		listener = socket(addr_buf->ai_family, addr_buf->ai_socktype, addr_buf->ai_protocol);
		if(listener == -1)
			continue;
		setsockopt(listener, SOL_SOCKET, SO_REUSEADDR, (char *)&optval, sizeof(optval));
		if(!bind(listener, addr_buf->ai_addr, addr_buf->ai_addrlen))
			break;
		close(listener);
	}
	freeaddrinfo(addr_res);
	return (addr_buf == NULL) ? 0 : listener;
}

void add_client_connection()
{
	int client = accept(listener, NULL, NULL);
	if(client < 0)
	{
		perror("accept()");
		return;
	}

	int i = find_first_free_index();
	if(i == -1)
	{
		fprintf(stderr, "Too many connections for new one");
		return;
	}
	printf("\n=================new connection======================%d\n", i);
	establish_events[i].fd = client;
	establish_events[i].events = POLLIN;

	events_cnt++;
	not_establish_cnt++;
}

bool establish_socks5_connection(int i)
{
	/*======= receive VERSION NMETHODS METHODS[]=======*/
	char nmethods = socks5_invitation(establish_events[i].fd);
	if(nmethods == -1)
		return false;

	/*======= send VERSION METHOD=======*/
	if(!socks5_set_method(establish_events[i].fd, nmethods))
		return false;

	/*======= receive VERSION CMD RESERVED ATYPE ADDR PORT=======*/
	char atype = socks5_type_read(establish_events[i].fd);
	if(atype == -1)
	{
		socks5_send_response(establish_events[i].fd, COMMAND_NOT_SUPPORTED);
		return false;
	}

	char *dest_addr;
	unsigned char size;
	int server = -1;

	if (atype == IPV4) 
		dest_addr = socks5_ip_read(establish_events[i].fd);
	else if (atype == DOMAIN) 
		dest_addr = socks5_domain_read(establish_events[i].fd, &size);
	else 
	{
		socks5_send_response(establish_events[i].fd, ATYPE_NOT_SUPPORTED);
		return false;
	}

	if(dest_addr == NULL)
		return false;

	char *dest_port = socks5_read_port(establish_events[i].fd);
	if(dest_port == NULL)
		return false;
	
	char rep = OK;
	/*======= open new connection to remote server======= */
	server = connect_to_server(dest_addr, dest_port);
	free(dest_addr);
	free(dest_port);
	if (server == -1) 
		rep = CONNECTION_REFUSED;

	/*======= send VERSION REP RESERVED ATYPE ADDR PORT=======*/
	socks5_send_response(establish_events[i].fd, rep);
	if(rep != OK)
		return false;

	outcoming_events[i].fd = server;
	incoming_events[i].fd = establish_events[i].fd;
	incoming_events[i].events = POLLIN;
	establish_events[i].events = 0;
	init_Attr(inAttrs, i);
	init_Attr(outAttrs, i);
	printf("\n=================connection established==================%d\n", i);
	return true;
}

int recv_from_client(int i) //receiving http-request from browser - 1
{
	int read = recv(incoming_events[i].fd, inAttrs[i].buf + inAttrs[i].end, inAttrs[i].size - inAttrs[i].end, 0);
	printf("\n=================recv some data from browser=========%d read: %d\n", i, read);

	if (read == -1)
	{
		perror("read()");
		return -1;
	}
	else if (read == 0) 
	{
		inAttrs[i].eof = true;
		return 0;
	}

	inAttrs[i].end += read;

	if (outAttrs[i].end == outAttrs[i].size)
		return 0;
		
	return 1;
}

int send_to_server(int i)	//sendind http-request to server - 2
{
	int written = send(outcoming_events[i].fd, inAttrs[i].buf, inAttrs[i].end - inAttrs[i].offset, 0);
	if (written == -1) 
	{
		perror("write()");
		return -1;
	}
	printf("\n=================send some data to server============%d written:%d\n", i, written);

	inAttrs[i].offset += written;

	if (inAttrs[i].offset == inAttrs[i].end)
	{
		inAttrs[i].offset = 0;
		inAttrs[i].end = 0;
		if(!inAttrs[i].eof)
			incoming_events[i].events |= POLLIN;
		return 0;
	}
	return 1;
}

int recv_from_server(int i)	//receiving answer from server - 3
{
	int read = recv(outcoming_events[i].fd, outAttrs[i].buf + outAttrs[i].end, outAttrs[i].size - outAttrs[i].end, 0);
	printf("\n=================recv some data from server==========%d read: %d\n", i, read);
	if(read == -1) 
	{
		perror("read()");
		return -1;
	}
	else if(read == 0) 
	{
		outAttrs[i].eof = true;
		return 0;
	}

	outAttrs[i].end += read;
	if (outAttrs[i].end == outAttrs[i].size)
	{
		outcoming_events[i].events &= ~POLLIN;
	}	
	return 1;
}

int send_to_client(int i)	//sending answer to browser - 4
{
	int written = send(incoming_events[i].fd, outAttrs[i].buf + outAttrs[i].offset, outAttrs[i].end - outAttrs[i].offset, 0);
	printf("\n=================send some data to browser===========%d written:%d\n", i, written);

	if (written == -1) 
	{
		perror("write()");
		return -1;
	}

	outAttrs[i].offset += written;
	if (outAttrs[i].offset == outAttrs[i].end)
	{
		if(outAttrs[i].eof)
			return -1;
		outAttrs[i].offset = 0;
		outAttrs[i].end = 0;
		return 0;	
	}
	return 1;
}

int main(int argc, char *argv[])
{
	/*==========socket setup==========*/
	const char *usage = "Usage: <programm name> <server port>";
	if(argc != 2)
	{
		fprintf(stderr, "%s\n", usage);
		exit(EXIT_FAILURE);
	}
	
	char *bind_port = argv[1];
	listener = bind_socket(bind_port);
	if(!listener) 
	{
		fprintf(stderr, "Couldn't bind\n");
		exit(EXIT_FAILURE);
	}
	if(listen(listener, SOMAXCONN))		
	{
		perror("listen()");			
		exit(EXIT_FAILURE);				
	}

	/*==========poll setup=========*/
	events = malloc(sizeof(struct pollfd) * (3 * FD_OPEN + 1));
	establish_events = events + 1;
	incoming_events = establish_events + FD_OPEN;
	outcoming_events = incoming_events + FD_OPEN;

	atexit(at_close);
	signal(SIGPIPE, SIG_IGN);
	signal(SIGINT, signal_end);
	signal(SIGTERM, signal_end);

	for(int i = 1; i < 3 * FD_OPEN + 1; i++)
	{
		events[i].fd = -1;
		events[i].events = 0;
	}
	events[0].fd = listener;
	events[0].events = POLLIN;

	while(!isEnd)
	{
		int ready_cnt = poll(events, 3 * FD_OPEN + 1, 5000);
		if(ready_cnt < 0)
		{
			perror("poll()");
			break;
		}
		else if(ready_cnt == 0)
			continue;

		/*ready for new connection*/
    	if(events[0].revents & POLLIN)
    	{
    		add_client_connection();
			ready_cnt--;
    	}

    	for(int i = 0; i < FD_OPEN && not_establish_cnt > 0; i++)
    	{
    		if(establish_events[i].revents & POLLIN)
    		{
    			if(!establish_socks5_connection(i))
    				close_connection(establish_events, i);
    			not_establish_cnt--;
    			ready_cnt--;
    		}
    	}

		for(int i = 0; i < FD_OPEN && ready_cnt > 0; i++)
		{
			/*recv request from browser*/
			if(incoming_events[i].revents & POLLIN)
			{
				int ret = recv_from_client(i);	//1
				if(ret == -1)	//error
				{
					printf("\n=================incoming fd closed==================%d\n", i);
					close_connection(incoming_events, i);
					establish_events[i].fd = -1;
				}
				else 
				{
					if(!ret)	
						incoming_events[i].events = POLLOUT;
					outcoming_events[i].events = POLLIN | POLLOUT;
				}
				ready_cnt--;
			}
			else if(incoming_events[i].revents & POLLOUT)
			{
				int ret = send_to_client(i);	//4
				if(ret == -1)
				{
					printf("\n=================incoming fd closed==================%d\n", i);
					close_connection(incoming_events, i);
					establish_events[i].fd = -1;
				}
				else if(!ret)
				{
					outcoming_events[i].events |= POLLIN;
					incoming_events[i].events &= ~POLLOUT;	
				}
				ready_cnt--;
			}
		}

		for(int i = 0; i < FD_OPEN && ready_cnt > 0; i++)
		{
			/*writting to the server*/
			if(outcoming_events[i].revents & POLLOUT)
			{
				int ret = send_to_server(i);	//2
				if(ret == -1)
				{
					printf("\n=================outcoming fd closed=================%d\n", i);
					close_connection(incoming_events, i);
					close_connection(outcoming_events, i);
					establish_events[i].fd = -1;
				}
				else if(!ret)
				{
					outcoming_events[i].events &= ~POLLOUT;
				}
				ready_cnt--;				
			}
			/*reading from the server*/
			else if(outcoming_events[i].revents & POLLIN)
			{
				int ret = recv_from_server(i);	//3
				if(ret < 1)
				{
					printf("\n=================outcoming fd closed=================%d\n", i);
					close_connection(outcoming_events, i);
				}
				else
					incoming_events[i].events |= POLLOUT;
				ready_cnt--;
			}			
		}
	}
	exit(EXIT_SUCCESS);
}