import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-hello',
  templateUrl: './hello.component.html',
  styleUrls: ['./hello.component.css']
})
export class HelloComponent implements OnInit {
  name = 'World';

  constructor() { }

  ngOnInit() {
  }

  setName(name) {
    this.name = name;
  }

}
